
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * This script evaluates participants' prediction files, returning the Precision, Recall and F-measure.
 * The script also displays wrong predictions by default.
 * 
 * USAGE:
 * 		java -jar BARR2_Evaluator.jar <GOLD_ANNOTATIONS_FILE_PATH> <PREDICTIONS_FILE_PATH> <TASK_NUMBER> <STOP_WORDS_FILE> <OPTIONAL_EXTRA_OUTPUT_DETAILS>
 * 
 * GOLD_ANNOTATIONS_FILE_PATH: path to the gold standard. When evaluating the training set, participants should use annotations files
 * 							as gold standard when evaluating their predictions on the training set.
 * PREDICTIONS_FILE_PATH: path the participant's prediction file. File must have the same format as the gold standard file.
 * TASK_NUMBER: participants must specify the number of the task to evaluate their predictions. 
 * 				Use "1" for the abbreviation-definition (short-long forms) pairs task.
 * 				Use "2" for the abbreviation resolution task. 				
 * STOP_WORDS_FILE: file which contains Spanish stop words, vital to evaluate the second task
 * OPTIONAL_EXTRA_OUTPUT_DETAILS: using this flag allows users to get extra information about their predictions, 
 * 									like which predictions are correct, and which were missed
 */

public class BARR_Evaluator {

	// Input variables
	private String goldStandard;
	private String predictionsFile;
	private int taskNumber;
	private String stopWordsFile;
	private boolean extraDetails;
	
	// These variables store mention and relation types for task 1
	private Map<String, Integer> mentionTypes;
	private Map<String, Integer> relationTypes;
	
	// These variables store the gold annotations and predictions to evaluate between them
	private Map<String, Map<Integer, String>> goldAnnotations;
	private Map<String, Map<Integer, String>> predictions;
	
	// This variable stores the Spanish stop words
	private Map<String, Integer> stopWordsMap;
	
	// These variables store the amount of correct, incorrect and missing predictions,
	// the number of predictions given by the participant
	// and the number of annotations present in the Gold Standard 
	//
	// There are 3 different scoring types integrated in this script: ultra-strict, strict and flexible.
	// For each type, a variable is used for correct and wrong predictions
	private float correctPredictionsUltraStrict;
	private float correctPredictionsStrict;
	private float correctPredictionsFlexible;
	private int missingPredictions;
	private int wrongPredictionsUltraStrict;
	private int wrongPredictionsStrict;
	private int wrongPredictionsFlexible;
	private int totalPredictions;
	private int totalAnnotationsGS;
	
	public BARR_Evaluator(String goldStandard, String predictionsFile, int taskNumber, String stopWordsFile, boolean extraDetails)
	{
		this.goldStandard = goldStandard;
		this.predictionsFile = predictionsFile;
		this.taskNumber = taskNumber;
		this.stopWordsFile = stopWordsFile;
		this.extraDetails = extraDetails;
		
		mentionTypes = new HashMap<String, Integer>();
		relationTypes = new HashMap<String, Integer>();
		
		goldAnnotations = new HashMap<String, Map<Integer, String>>();
		predictions = new HashMap<String, Map<Integer, String>>();
		
		stopWordsMap = new HashMap<String, Integer>();
		
		correctPredictionsUltraStrict = 0;
		correctPredictionsStrict = 0;
		correctPredictionsFlexible = 0;
		missingPredictions = 0;
		wrongPredictionsUltraStrict = 0;
		wrongPredictionsStrict = 0;
		wrongPredictionsFlexible = 0;
		totalPredictions = 0;
		totalAnnotationsGS = 0;
	}
	
	public static void main(String[] args) throws IOException 
	{
		// Load arguments
		String goldStandard = args[0];
		String predictionsFile = args[1];
		int taskNumber = Integer.parseInt(args[2]);
		
		// Check if task numbers are correct 
		if (taskNumber < 1 || taskNumber > 2)
		{
			DisplayError("GeneralError");
		}
		
		// Load stop words argument
		String stopWordsFile = args[3];
		
		// Check if participant wants more information in the output
		boolean extraDetails = false;
		if (args.length == 5)
		{
			extraDetails = Boolean.parseBoolean(args[4]);
		}		
		
		// Execute program
		BARR_Evaluator evaluation = new BARR_Evaluator(goldStandard, predictionsFile, taskNumber, stopWordsFile, extraDetails);
		evaluation.start();
	}

	/*
	 * This method initializes all the methods for evaluation.
	 * The evaluation begins with file controls, checking if the structure fits with the chosen task, 
	 * 		for both gold standard file, and predictions files. 
	 * Once both files passed the control, the script evaluates the predictions, displaying the 
	 * 		erroneous predictions, together with the correct predictions and missed ones if desired by the user.
	 * Finally, the script displays the precision, recall and F-Measure.
	 */
	public void start() throws IOException 
	{
		printInitialInfo();
		
		// If we are evaluating detected abbreviations and their explicit definitions, we need to initialize the mention and relation types
		if (taskNumber == 1)
		{
			initializeMentionRelationTypes();
		}		
		
		// Check if gold annotation and prediction files are correct
		boolean goldCorrect = checkAnnotations(goldStandard, true);
		boolean predictionsCorrect = checkAnnotations(predictionsFile, false);
		
		if (goldCorrect && predictionsCorrect)
		{
			// Begin evaluation if files are correct
			if (taskNumber == 2)
			{
				loadStopWords();	// Stop words are needed for a flexible evaluation of abbreviation definitions in task 2
			}			
			evaluate();
			
			// There are 3 evaluation methods for sub-track 2. The script displays all of them for this track.
			// For task 1, only the default evaluation type is displayed.
			printFinalResultsUltraStrict();
			if (taskNumber == 2)
			{
				printFinalResultsStrict();
				printFinalResultsFlexible();
			}				
		}		
		else
		{
			// Display error messages if one of the file is incorrect
			if (!goldCorrect)
			{
				DisplayError("GoldError");
			}			
			else if (!predictionsCorrect)
			{
				DisplayError("PredictionError");
			}
			else if (!goldCorrect && !predictionsCorrect)
			{
				DisplayError("FatalError");
			}
		}
	}
	
	/*
	 * This method just prints the input information for the participant
	 */
	public void printInitialInfo()
	{
		System.out.println("\tGold Standard annotation file:\t" + goldStandard);
		System.out.println("\tPredictions file:\t\t" + predictionsFile);
		if (taskNumber == 1)
		{
			System.out.println("\tTask: 1 --> Abbreviation-definition (short-long forms) relations task.");
		}
		else
		{
			System.out.println("\tTask: 2 --> Abbreviation recognition task.");
		}
		System.out.println(); // empty line
	}
	
	/*
	 * This method initializes the mention and relation types to evaluate relations between abbreviations and their explicit definitions 
	 */
	public void initializeMentionRelationTypes()
	{
		System.out.println("Initializing mention and relation types...");
		
		mentionTypes.put("SHORT_FORM", 1);
		mentionTypes.put("LONG_FORM", 1);
		mentionTypes.put("NESTED", 1);
		
		relationTypes.put("SHORT-LONG", 1);
		relationTypes.put("SHORT-NESTED", 1);
		relationTypes.put("NESTED-LONG", 1);
	}
	
	/*
	 * This file checks if the gold annotations' or predictions file's structure fits with the task to evaluate.
	 * If the 
	 */
	public boolean checkAnnotations(String file, boolean isGold) throws IOException
	{
		if (isGold)
		{
			System.out.println("Checking Gold Standard annotations file ...");
		}
		else
		{
			System.out.println("Checking predictions file ...");
		}
		
		boolean goldCorrect = true;
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = "";
		int numLine = 0;
		while ((line = reader.readLine()) != null)
		{
			numLine++;
			if (!line.startsWith("#"))	// Avoid first line
			{				
				if (taskNumber == 1)
				{
					boolean lineCorrect = checkShortLongTaskCorrectLine(line);
					if (!lineCorrect)	// There is an error in the annotation's format, return false and end script
					{
						goldCorrect = false;
						System.err.println("ERROR IN LINE " + numLine + " : " + line);
						break;
					}
					else				// There is no error in the annotation's format, store the annotation
					{
						String documentID = line.split("\t")[0];
						int startOffset = Integer.parseInt(line.split("\t")[2]);
						if (isGold)
						{							
							Map<Integer, String> documentMap = new HashMap<Integer, String>();
							if (goldAnnotations.containsKey(documentID))
							{
								documentMap = goldAnnotations.get(documentID);
							}
							documentMap.put(startOffset, line);
							goldAnnotations.put(documentID, documentMap);
							
							// Increase the number of total annotations found in Gold Standard
							totalAnnotationsGS++;
						}
						else
						{
							Map<Integer, String> documentMap = new HashMap<Integer, String>();
							if (predictions.containsKey(documentID))
							{
								documentMap = predictions.get(documentID);
							}
							documentMap.put(startOffset, line);
							predictions.put(documentID, documentMap);
							
							// Increase the number of total predictions made by the participant,
							// this variable is increased only if the document ID is also present in the gold standard
							if (goldAnnotations.containsKey(documentID))
							{
								totalPredictions++;
							}							
						}
					}
				}
				else if (taskNumber == 2)
				{					
					boolean lineCorrect = checkAbbreviationTaskCorrectLine(line);
					if (!lineCorrect)	// There is an error in the annotation's format, return false and end script
					{
						goldCorrect = false;
						System.err.println("ERROR IN LINE " + numLine + " : " + line);
						break;
					}
					else				// There is no error in the annotation's format, store the annotation
					{
						String documentID = line.split("\t")[0];
						int startOffset = Integer.parseInt(line.split("\t")[1]);
						if (isGold)
						{							
							Map<Integer, String> documentMap = new HashMap<Integer, String>();
							if (goldAnnotations.containsKey(documentID))
							{
								documentMap = goldAnnotations.get(documentID);
							}
							documentMap.put(startOffset, line);
							goldAnnotations.put(documentID, documentMap);
							
							// Increase the number of total annotations found in Gold Standard
							totalAnnotationsGS++;
						}
						else
						{
							Map<Integer, String> documentMap = new HashMap<Integer, String>();
							if (predictions.containsKey(documentID))
							{
								documentMap = predictions.get(documentID);
							}
							documentMap.put(startOffset, line);
							predictions.put(documentID, documentMap);
							
							// Increase the number of total predictions made by the participant,
							// this variable is increased only if the document ID is also present in the gold standard
							if (goldAnnotations.containsKey(documentID))
							{
								totalPredictions++;
							}
						}
					}
				}
			}
		}
		reader.close();
		
		return goldCorrect;
	}
	
	public boolean checkAbbreviationTaskCorrectLine(String line)
	{
		// first, check if the tabular file has 6 columns 
		String elements[] = line.split("\t");
		if (elements.length != 6)
		{
			return false;
		}
		
		// first, check if the element in the first column looks like the document ID
		if (!elements[0].startsWith("S") || !elements[0].substring(5, 6).equals("-") || !elements[0].substring(23, 24).equals("-"))
		{
			return false;
		}
		
		// then, check if the second and third columns contain numerical content
		try
		{
			int start = Integer.parseInt(elements[1]);
			int end = Integer.parseInt(elements[2]);
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		
		// if we arrived here, everything was fine
		return true;
	}
	
	public boolean checkShortLongTaskCorrectLine(String line)
	{
		// first, check if the tabular file has 10 columns 
		String elements[] = line.split("\t");
		if (elements.length != 9)
		{
			return false;
		}
		
		// first, check if the element in the first column looks like the document ID
		if (!elements[0].startsWith("S") || !elements[0].substring(5, 6).equals("-") || !elements[0].substring(23, 24).equals("-"))
		{
			return false;
		}
		
		// then, check if the third, fourth, eighth and ninth columns contain numerical content
		try
		{
			int startA = Integer.parseInt(elements[2]);
			//int endA = Integer.parseInt(elements[3]);
			int startB = Integer.parseInt(elements[6]);
			int endB = Integer.parseInt(elements[7]);
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		
		// finally, check if mention types (columns 2 and 7) and relation types (column 6) are correct
		if (!mentionTypes.containsKey(elements[1]) || !mentionTypes.containsKey(elements[5]))
		{
			return false;
		}
		if (!relationTypes.containsKey(elements[4]))
		{
			return false;
		}
		
		// if we arrived here, everything was fine
		return true;
	}
	
	/*
	 * This method loads the stop words. These will be used later to evaluate the predicted definition for recognized abbreviations.
	 * Stop words file format must have just one stop word per line. Use "#" for comments
	 */
	public void loadStopWords() throws IOException
	{
		BufferedReader reader = new BufferedReader(new FileReader(stopWordsFile));
		String line = "";
		while ((line = reader.readLine()) != null)
		{
			if (!line.startsWith("#")) // ignore comments
			{
				stopWordsMap.put(line, 1);
			}
		}
		reader.close();
	}
	
	/*
	 * This method evaluates participants' predictions against gold annotations
	 */
	public void evaluate()
	{
		System.out.println("Evaluating predictions against the Gold Standard...");
		
		Iterator<String> predictionIterID = predictions.keySet().iterator();
		while (predictionIterID.hasNext())
		{
			String documentID = predictionIterID.next();
			
			// If gold annotations do not contain the document to evaluate, throw an error message and check next document
			if (!goldAnnotations.containsKey(documentID))
			{
		//		System.err.println("Error: document ID " + documentID + " does not exist in Gold Standard. Please check your predictions file.");
			}
			else	// Document exists in gold annotations, check predictions
			{
				Map<Integer, String> documentPredictions = predictions.get(documentID);
				Map<Integer, String> documentAnnotationsGS = goldAnnotations.get(documentID);
				
				Iterator<Integer> goldIter = documentAnnotationsGS.keySet().iterator();
				while (goldIter.hasNext())
				{
					/*
					 * This part of the method analyzes if the gold annotation is present in the predictions file.
					 * It checks if there is a prediction with the same start offset of the gold annotation.
					 */
					int startOffset = goldIter.next();
					if (documentPredictions.containsKey(startOffset))
					{
						// This annotation could exist in the prediction file, here we check the content.
						String gsLine = documentAnnotationsGS.get(startOffset);
						String predictionLine = documentPredictions.get(startOffset);
						
						float guessed = 0;
						if (taskNumber == 1)
						{
							// check if the abbreviation-definition relation is correct
							guessed = evaluateRelation(gsLine, predictionLine);
						}
						else
						{
							// check if the abbreviation resolution is correct
							guessed = evaluateAbbreviationRecognition(gsLine, predictionLine);
							if (guessed == 0)
							{
								//System.out.println("WRONG: '" + predictionLine + "' --> Error in abbreviation disambiguation. Correct definition: " + gsLine.split("\t")[4]);
							}
						}
						
						if (guessed == 1)
						{
							// The annotation is correct
							correctPredictionsFlexible++;
							correctPredictionsUltraStrict++;
							correctPredictionsStrict++;
							if (extraDetails)	// print missing info (if desired by the participant)
							{
								//System.out.println("CORRECT: '" + predictionLine + "'"); 
							}
						}
						else if (guessed == 0)
						{
							// The annotation is wrong
							wrongPredictionsFlexible++;
							wrongPredictionsUltraStrict++;
							wrongPredictionsStrict++;
							// print missing info (always print by default)
				//			System.out.println("WRONG: '" + predictionLine + "'");
						}
						else if (guessed == 2)
						{
							// the annotation is wrong for ultra-strict, but correct for strict and flexible
							correctPredictionsFlexible++;
							correctPredictionsStrict++;
							wrongPredictionsUltraStrict++;
							if (extraDetails)	// print missing info (if desired by the participant)
							{
								//System.out.println("CORRECT (non ultra-strict): '" + predictionLine + "'"); 
							}
						}
						else if (guessed < 1 && guessed > 0)
						{
							// The annotation is partially correct
							correctPredictionsFlexible = correctPredictionsFlexible + guessed;
							wrongPredictionsUltraStrict++;
							wrongPredictionsStrict++;
							// print missing info (always print by default)
							System.out.println("PARTIALLY CORRECT: '" + predictionLine + "' : " + guessed + " . Correct definition: " + gsLine.split("\t")[4]);
						}
					}
					else
					{
						// The annotation is missing
						missingPredictions++;
						if (extraDetails)	// print missing info (if desired by the participant)
						{
							String gsLine = documentAnnotationsGS.get(startOffset);
							System.out.println("MISSING: '" + gsLine + "'"); 
						}
					}
				}
				
				// The following lines checks if there are extra predictions given by the participant, but not in GS
				// All these predictions are counted as wrong
				Iterator<Integer> predictionIter = documentPredictions.keySet().iterator();
				while (predictionIter.hasNext())
				{
					int startOffset = predictionIter.next();
					if (!documentAnnotationsGS.containsKey(startOffset))
					{
						String predictionLine = documentPredictions.get(startOffset);
						// Prediction not found in GS
						// The annotation is wrong
						wrongPredictionsFlexible++;
						wrongPredictionsUltraStrict++;
						wrongPredictionsStrict++;
						// print missing info (always print by default)
						//System.out.println("COMPLETELY WRONG: '" + predictionLine + "'");
					}
				}
			}
		}
	}
	
	/*
	 * This method check if the prediction given for the abbreviation-definition (short-long forms) sub-task is correct
	 */
	public float evaluateRelation(String gsLine, String predictionLine)
	{
		String elementsGS[] = gsLine.split("\t");
		String elementsPred[] = predictionLine.split("\t");
		
		//Elimino los acentos
		eliminoAcentos(elementsGS);
		eliminoAcentos(elementsPred);
		
		///
		/// Argument A
		///
		// First check if the mention A type (column 2) matches in prediction and gold annotation
		if (!elementsGS[1].equals(elementsPred[1]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in mention A type.");
			return 0;
		}
		
		// Next check if the mention A end offset (column 4) matches in prediction and gold annotation
		// Start offset is not analyzed, because we already know it matches with the gold annotation.
		/*if (!elementsGS[3].equals(elementsPred[3]))
		{
			System.out.println("WRONG: '" + predictionLine + "' --> Error in mention A end offset.");
			return 0;
		}*/
				
		// Finally check if the mention A text (column 5) matches in prediction and gold annotation
		if (!elementsGS[3].equals(elementsPred[3]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in mention A text.");
			return 0;
		}
		
		
		///
		/// Argument B
		///
		// First check if the mention B type (column 7) matches in prediction and gold annotation
		if (!elementsGS[5].equals(elementsPred[5]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in mention B type.");
			return 0;
		}
		
		// Next check if the mention B start offset (column 8) matches in prediction and gold annotation
		if (!elementsGS[6].equals(elementsPred[6]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in mention B start offset.");
			return 0;
		}
		
		// Then check if the mention B end offset (column 9) matches in prediction and gold annotation
		if (!elementsGS[7].equals(elementsPred[7]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in mention B end offset.");
			return 0;
		}
				
		// Finally check if the mention B text (column 10) matches in prediction and gold annotation
		if (!elementsGS[8].equals(elementsPred[8]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in mention B text.");
			return 0;
		}
		
		
		///
		/// Relation
		///
		// Check if the relation type (column 6) matches in prediction and gold annotation
		if (!elementsGS[5].equals(elementsPred[5]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in relation type.");
			return 0;
		}
		
		// If we arrived here, the annotation is correct
		return 1;
	}
	
	private void eliminoAcentos(String[] elementsGS) {
		//Accedo a las posiciones 3 y 8 para tratar el texto de la expansion		
		String cadenaNormalize = Normalizer.normalize(elementsGS[3], Normalizer.Form.NFD);   
		elementsGS[3] = cadenaNormalize.replaceAll("[^\\p{ASCII}]", "");
		cadenaNormalize = Normalizer.normalize(elementsGS[8], Normalizer.Form.NFD);   
		elementsGS[8] = cadenaNormalize.replaceAll("[^\\p{ASCII}]", "");		
	}

	/*
	 * This method check if the prediction given for the abbreviation recognition sub-task is correct.
	 * 
	 * The evaluation of the abbreviation's meaning will be flexible.
	 * Participants need to detect the abbreviation's exact text and position in the clinical case,
	 * otherwise the prediction will be completely wrong.
	 * 
	 * Meanwhile, the evaluation of the definition is much more complex. If the participant finds the exact position
	 * and text of the abbreviation, we evaluate the definition the following way:
	 * - Check if the definition provided by the system matches with the definition in Gold Standard --> if so, 1 point
	 * - If not, check if the lemmatized definition of the GS and prediction match --> if so, 1 point
	 * - If not, remove stop words and check how many tokens in prediction and gold annotation match.
	 * 		- We calculate the percentage of matching tokens in the prediction. We get the percentage by dividing the 
	 * 		  number of tokens matched with the maximun number of tokens between the prediction and gold annotation.
	 * 		  Examples: 
	 * 			- If 3 tokens of prediction match against 4 tokens in gold: 3/4 = 0.75 points
	 * 			- If the prediction has 5 tokens but gold has 4, and 4 of the prediction match, then 4/5 = 0.8 points
	 * 		- Do this operation in both default definition and lemmatized definition. Return highest mark.
	 * 			- Default definition = %66 match --> return 0.66 points
	 * 			- Lemmatized definition = %33 match --> ignore! 
	 */
	public float evaluateAbbreviationRecognition(String gsLine, String predictionLine)
	{
		String elementsGS[] = gsLine.split("\t");
		String elementsPred[] = predictionLine.split("\t");
		
		eliminoAcentosTask2(elementsGS);
		eliminoAcentosTask2(elementsPred);
		
		// First check if the mention end offset (column 3) matches in prediction and gold annotation
		// Start offset is not analyzed, because we already know it matches with the gold annotation.
		if (!elementsGS[2].equals(elementsPred[2]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in abbreviation end offset.");
			return 0;
		}
				
		// Finally check if the mention text (column 4) matches in prediction and gold annotation
		if (!elementsGS[3].equalsIgnoreCase(elementsPred[3]))
		{
			//System.out.println("WRONG: '" + predictionLine + "' --> Error in abbreviation text.");
			return 0;
		}
		
		
		/// 
		/// Definition
		///
		// First check if the abbreviation's unlemmatized definition (column 5) matches in prediction and gold annotation
		// If it matches, return true, if not, check lemmatized definition
		if (!elementsGS[4].equalsIgnoreCase(elementsPred[4]))
		{
			// Predicted abbreviation definition does not match with gold definition
			// Check lemmatized definition (column 6)
			if (!elementsGS[5].equalsIgnoreCase(elementsPred[5]))
			{
				// if we arrived here, ultra-strict evaluation will score 0
				// analize prediction tokens
				float score = analizeTokens(elementsPred[4], elementsGS[4], elementsPred[5], elementsGS[5]);
				
				// If we scored 1, that means both the prediction and gold annotation have the same amount of tokens,
				// and all these tokens match. The Score here for ultra-strict evaluation is 0, but 1 for both strict
				// evaluation and flexible evaluation.
				// The reason why we return 2 in the following statement is to know when to consider the result is correct 
				// for strict evaluation, but incorrect for ultra-strict.
				if (score == 1.0)
				{
					return 2;
				}
				else
				{
					return score;
				}
			}			
		}
		
		// if everything matches, return 1 point
		return 1;
	}
	
	private void eliminoAcentosTask2(String[] elementsPred) {
		String cadenaNormalize = Normalizer.normalize(elementsPred[3], Normalizer.Form.NFD);   
		elementsPred[3] = cadenaNormalize.replaceAll("[^\\p{ASCII}]", "");
		cadenaNormalize = Normalizer.normalize(elementsPred[4], Normalizer.Form.NFD);   
		elementsPred[4] = cadenaNormalize.replaceAll("[^\\p{ASCII}]", "");	
		cadenaNormalize = Normalizer.normalize(elementsPred[5], Normalizer.Form.NFD);   
		elementsPred[5] = cadenaNormalize.replaceAll("[^\\p{ASCII}]", "");
		
	}

	public float analizeTokens(String pred, String gs, String predLemma, String gsLemma) 
	{
		// Create lists of tokens
		List<String> predTokens = new ArrayList<String>(Arrays.asList(pred.split(" ")));
		List<String> gsTokens = new ArrayList<String>(Arrays.asList(gs.split(" ")));
		List<String> predLemmaTokens = new ArrayList<String>(Arrays.asList(predLemma.split(" ")));
		List<String> gsLemmaTokens = new ArrayList<String>(Arrays.asList(gsLemma.split(" ")));
		
		// Remove stop words from gold annotations
		List<String> gsTokensTemp = new ArrayList<String>();	// new unlemmatized gold annotation
		for (int i = 0; i < gsTokens.size(); i++)
		{
			String token = gsTokens.get(i);
			if (!stopWordsMap.containsKey(token))
			{
				gsTokensTemp.add(token);
			}
		}
		gsTokens = gsTokensTemp;
		
		// Remove stop words from gold annotations
		List<String> gsLemmaTokensTemp = new ArrayList<String>();	// new lemmatized gold annotation
		for (int i = 0; i < gsLemmaTokens.size(); i++)
		{
			String token = gsLemmaTokens.get(i);
			if (!stopWordsMap.containsKey(token))
			{
				gsLemmaTokensTemp.add(token);
			}
		}
		gsLemmaTokens = gsLemmaTokensTemp;
		
		// Check tokens present in unlemmatized prediction and unlemmatized gold annotation
		float matchingTokens = 0;
		for (int i = 0; i < predTokens.size(); i++)
		{
			String token = predTokens.get(i);
			// Check if token is stop word, continue if not
			if (!stopWordsMap.containsKey(token))
			{
				if (gsTokens.contains(token))
				{
					matchingTokens++;
				}
			}			
		}
		// check which has most tokens, if gold annotation, or prediction
		float matchingScore = matchingTokens / max(gsTokens.size(), predTokens.size());
		
		// Check tokens present in lemmatized prediction and lemmatized gold annotation
		float matchingTokensLemma = 0;
		for (int i = 0; i < predLemmaTokens.size(); i++)
		{
			String token = predLemmaTokens.get(i);
			// Check if token is stop word, continue if not
			if (!stopWordsMap.containsKey(token))
			{
				if (gsLemmaTokens.contains(token))
				{
					matchingTokensLemma++;
				}
			}			
		}
		// check which has most tokens, if gold annotation lemmatized, or prediction lemmatized
		float matchingScoreLemma = matchingTokensLemma / max(gsLemmaTokens.size(), predLemmaTokens.size());
		
		if (matchingScoreLemma > matchingScore)
		{
			return matchingScoreLemma;
		}
		else
		{
			return matchingScore;
		}
	}
	
	/*
	 * This method displays the precision, recall and F-Measure of the ultra-strict evaluation
	 */
	public void printFinalResultsUltraStrict()
	{
		System.out.println(); // empty line
		System.out.println("ULTRA-STRICT EVALUATION:");
		System.out.println("---------------------------");
		System.out.println("CORRECT PREDICTIONS = " + correctPredictionsUltraStrict);
		System.out.println("MISSED PREDICTIONS = " + missingPredictions);
		System.out.println("WRONG PREDICTIONS = " + wrongPredictionsUltraStrict);
		System.out.println("TOTAL PREDICTIONS = " + totalPredictions);
		System.out.println("TOTAL ANNOTATIONS GS = " + totalAnnotationsGS);
		System.out.println(); // empty line
		
		float precision = (float) correctPredictionsUltraStrict / (float) totalPredictions;
		float recall = (float) correctPredictionsUltraStrict/ (float) totalAnnotationsGS;
		float F1 = (2 * precision * recall) / (precision + recall);
		
		System.out.println("PRECISION = " + precision + " = " + correctPredictionsUltraStrict + " / " + totalPredictions);
		System.out.println("RECALL = " + recall + " = " + correctPredictionsUltraStrict + " / " + totalAnnotationsGS);
		System.out.println("F-MEASURE = " + F1);
		
		System.out.println("===========================");
	}
	
	/*
	 * This method displays the precision, recall and F-Measure of the strict evaluation
	 */
	public void printFinalResultsStrict()
	{
		System.out.println(); // empty line
		System.out.println("STRICT EVALUATION:");
		System.out.println("---------------------------");
		System.out.println("CORRECT PREDICTIONS = " + correctPredictionsStrict);
		System.out.println("MISSED PREDICTIONS = " + missingPredictions);
		System.out.println("WRONG PREDICTIONS = " + wrongPredictionsStrict);
		System.out.println("TOTAL PREDICTIONS = " + totalPredictions);
		System.out.println("TOTAL ANNOTATIONS GS = " + totalAnnotationsGS);
		System.out.println(); // empty line
		
		float precision = (float) correctPredictionsStrict / (float) totalPredictions;
		float recall = (float) correctPredictionsStrict / (float) totalAnnotationsGS;
		float F1 = (2 * precision * recall) / (precision + recall);
		
		System.out.println("PRECISION = " + precision + " = " + correctPredictionsStrict + " / " + totalPredictions);
		System.out.println("RECALL = " + recall + " = " + correctPredictionsStrict + " / " + totalAnnotationsGS);
		System.out.println("F-MEASURE = " + F1);
		System.out.println("===========================");
	}

	/*
	 * This method displays the precision, recall and F-Measure of the flexible evaluation.
	 */
	public void printFinalResultsFlexible()
	{
		System.out.println(); // empty line
		System.out.println("FLEXIBLE EVALUATION:");
		System.out.println("---------------------------");
		System.out.println("CORRECT PREDICTIONS = " + correctPredictionsFlexible);
		System.out.println("MISSED PREDICTIONS = " + missingPredictions);
		System.out.println("WRONG PREDICTIONS = " + wrongPredictionsFlexible);
		System.out.println("TOTAL PREDICTIONS = " + totalPredictions);
		System.out.println("TOTAL ANNOTATIONS GS = " + totalAnnotationsGS);
		System.out.println(); // empty line
		
		float precision = (float) correctPredictionsFlexible / (float) totalPredictions;
		float recall = (float) correctPredictionsFlexible / (float) totalAnnotationsGS;
		float F1 = (2 * precision * recall) / (precision + recall);
		
		System.out.println("PRECISION = " + precision + " = " + correctPredictionsFlexible + " / " + totalPredictions);
		System.out.println("RECALL = " + recall + " = " + correctPredictionsFlexible + " / " + totalAnnotationsGS);
		System.out.println("F-MEASURE = " + F1);
	}

	private static void DisplayError(String errorName) 
	{
		if (errorName.equals("GeneralError"))
		{
			System.err.println("USAGE:");
			System.err.println("\tjava -jar Evaluation.jar <GOLD_STANDARD_FILE_PATH> <PREDICTIONS_FILE_PATH> <TASK_NUMBER> <OPTIONAL_EXTRA_OUTPUT_DETAILS>:");
			System.err.println("GOLD_STANDARD_FILE_PATH: path to the gold standard. When evaluating the training set, participants should use annotations files" 
						+ " as gold standard when evaluating their predictions on the training set.");
			System.err.println("PREDICTIONS_FILE_PATH: path the participant's prediction file. File must have the same format as the gold standard file.");
			System.err.println("TASK_NUMBER: participants must specify the number of the task to evaluate their predictions:");
			System.err.println("\t\t\tUse \"1\" for the short-long forms pairs task.");
			System.err.println("\t\t\tUse \"2\" for the abbreviation resolution task.");
			System.err.println("OPTIONAL_EXTRA_OUTPUT_DETAILS: using this flag allows users to get extra information about their predictions,"
						+ " like which predictions are correct, and which were missed");
		}
		else if (errorName.equals("GoldError"))
		{
			System.err.println("There are errors in the gold standard file.\n" 
						+ "Please check if you are using the correct annotations file,\n"  
						+ "also check if the file fits with the right task number to evaluate.");
		}
		else if (errorName.equals("PredictionError"))
		{
			System.err.println("There are errors in the predictions file.\n" 
						+ "Please check if your predictions file has the same format as the gold annotations,\n" 
						+ "also check if you are using the correct predictions file with the right task number to evaluate.");
		}
		else if (errorName.equals("FatalError"))
		{
			System.err.println("There are errors in the gold standard and predictions file.\n" 
					+ "Please check if you are using the correct files,\n"  
					+ "also check if both files fit with the right task number to evaluate.");
		}
		
		System.exit(0);
	}
	
	private int max(int gsSize, int predictionSize)
	{
		if (gsSize > predictionSize)
		{
			return gsSize;
		}
		else
		{
			return predictionSize;
		}
	}
}
