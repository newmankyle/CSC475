/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package counterpoint;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import vmm.algs.DCTWPredictor;

/**
 *
 * @author Rob
 */
public class CounterpointGenerator {
    
    public static byte[] noteToMidi(byte[] testPattern, byte offset){
        //byte offset = (byte)(root+(testPattern[0]<1?48:36));
            for(int j = 0; j < testPattern.length-1; j++)
                testPattern[j] += offset;
        return testPattern;
    }
    
    public static int chooseNoteIndex(double[] probabilityArray){
        //Searches the array given a randomized key. Returns -1 if nothing found.
        int nextInd = Arrays.binarySearch(probabilityArray, Math.random());
        if(nextInd < 0)
            nextInd = -1-nextInd;
        return nextInd;
    }
    
    public static double[] calculateNoteProbabilities(double[] probabilityArray, double[] harmProbs){
        double harmSum = Arrays.stream(harmProbs).sum();
        Arrays.setAll(probabilityArray, k -> probabilityArray[k]/harmSum*harmProbs[k]);
        // then normalizes the values.
        double toNormalize = Arrays.stream(probabilityArray).sum();
        Arrays.setAll(probabilityArray, k -> probabilityArray[k]/toNormalize);
                
        // adds the previous note prob to the current one.
        for(int k = 1; k < harmProbs.length; k++)
            probabilityArray[k] += probabilityArray[k-1];
        
        return probabilityArray;
    }
    
    public static void printStats(byte[] testPattern, String testHarmony, String score){
        System.out.println("testPattern " + ": " + Arrays.toString(testPattern));
        System.out.println("testHarmony " + ": " + testHarmony);
        System.out.println("evaluation " + ": " + score);        
    }
    
    public static void markovGenerator(int testNum, int noteNum, byte[][] inputNotes, DCTWPredictor vmm, MarkovChain melodyModel, List<MarkovChain> constraint){
        byte root = 0;
        //Generate 10 Test patterns using only the markov chain
        for(int i = 0; i < 10; i++){
            byte[] testPattern = MarkovChain.getSeqFromConstraints(constraint);
            byte offset = (byte)(root+(testPattern[0]<1?48:36));
            testPattern = noteToMidi(testPattern, root);           
            
            byte[] harmony = new byte[noteNum];
            for(int j = 0; j < noteNum; j++)
                harmony[j] = (byte)(inputNotes[j][0]-testPattern[j]);
            
            String harmonyTS = Arrays.toString(harmony);
            String testHarmony = harmonyTS.substring(1,harmonyTS.length()-1).replace(",", ":4")+":4 !";
            String score = "" + (vmm.logEval(testHarmony, "! ")/testHarmony.length());
            
            printStats(testPattern, testHarmony, score);
            System.out.println();
        }
        
    }
    
    public static void main(String[] args) throws FileNotFoundException{
        
        /*****************************************
        * parses and initializes a test melody.
        */
        String testMelody = "60:4 62:4 61:4 63:4 65:4 68:4 67:4 71:4 72:4";
        byte root = 0;
        String tonality = "minor";
        //inputNotes: length 9 x 2 (44-36+1=9)
        byte[][] inputNotes = new byte[testMelody.length()-testMelody.replace(" ", "").length()+1][2];
        int noteNum = inputNotes.length;
        Scanner inputParser = new Scanner(testMelody);
        for(int i = 0; inputParser.hasNext(); i++){
            String currentNote = inputParser.next();
            //NOTE is substring from start to indexOf(:), RHYTHM is index(:)+1 to end.
            inputNotes[i][0] = Byte.parseByte(currentNote.substring(0,currentNote.indexOf(':')));
            inputNotes[i][1] = Byte.parseByte(currentNote.substring(currentNote.indexOf(':')+1));
        }
                
        DCTWPredictor harmonyModel = BachAnalysis.harmonyModel(tonality);
        MarkovChain melodyModel = DataParser.melodyModel("bass", tonality);
        
        //choices: all note choices given the input type (bass/soprano)
        byte[] choices = Arrays.copyOf(melodyModel.getLabels(), melodyModel.dim()-1);
        System.out.println("The choices are: " + Arrays.toString(choices));
        
        // Stupid are the values -1 to -8, and 9. Dumb is an array of max values 127.
        int[] stupid = new int[noteNum];
        Arrays.setAll(stupid, i -> (i+1)*(i<8?-1:1));
        byte[] dumb = new byte[noteNum];
        Arrays.fill(dumb, Byte.MAX_VALUE);
        List<MarkovChain> constraint = melodyModel.induceConstraints(noteNum+1,stupid,dumb);

        markovGenerator(10, noteNum, inputNotes, harmonyModel, melodyModel, constraint);
        System.out.println();
        
        //Generate 10 test patterns using the markov chain and vmm.
        for(int i = 0; i < 10; i++){
            byte[] testPattern = new byte[noteNum+1];
            byte[] actualChoices = Arrays.copyOf(choices, choices.length);
            //note: choices is all notes in the range of the input file (bass/soprano).
            
            //adds 48 or 36 to choices depending on the register (less than C).
            for(int j = 0; j < choices.length; j++)
                actualChoices[j] += (choices[j]<1?48:36)+root;
            //System.out.println("actualChoices2 " + i + ": " + Arrays.toString(actualChoices));
            
            byte offset = root;
            testPattern[0] = 127; // start sequence with "!"
            String harmonySequence = "! "; //more descriptive
            
            for(int j = 1; j < noteNum+1; j++){
                
                int x = j;                
                double[] harmProbs = new double[choices.length];
                // currentProbs: list of transitions from a given note in the Markov Chain.
                double[] currentProbs = Arrays.copyOf(constraint.get(j-1).getProbs(testPattern[j-1]), choices.length);                
                byte[] currentHarms = new byte[choices.length];
                
                // currentHarms: the harmonies between the current inputNote and all choices.
                for(int k = 0; k < choices.length; k++)
                    currentHarms[k] = (byte)(inputNotes[j-1][0]-actualChoices[k]);                
                String partialSequence = harmonySequence;    // "final or effectively final" blah blah blah
                
                //harmProbs: parses currentHarms into ' harm:dur harm:dur ... ! ' form, then calls call VMM predict.
                //  calculates the probabilities of each currenHarm given the harmony sequence so far (testHarmony).
                Arrays.setAll(harmProbs, k -> harmonyModel.predict(Byte.toString(currentHarms[k])+":4"+(x==noteNum?" !":""), partialSequence));
                
                currentProbs = calculateNoteProbabilities(currentProbs, harmProbs);
                
                //Searches the array given a randomized key. Returns -1 if nothing found.
                int nextInd = chooseNoteIndex(currentProbs);
               
                //adds the note and harmony value to the sequences.
                testPattern[j] = choices[nextInd];
                harmonySequence += Byte.toString(currentHarms[nextInd])+":4 ";
                
                // figures out the register from the first note in the sequence.
                if(j == 1){
                    offset = (byte)(actualChoices[nextInd]-testPattern[j]);
                    for(int k = 0; k < choices.length; k++)
                        actualChoices[k] = (byte)(choices[k]+offset);
                }
            }
            harmonySequence += "!";
            for(int j = 1; j < noteNum+1; j++)
                testPattern[j] += offset;
            
            String score = "" + harmonyModel.logEval(harmonySequence.substring(2), "! ")/(harmonySequence.length()-2);          
            printStats(testPattern, harmonySequence, score);
            System.out.println();
        }
    }
}
