/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package counterpoint;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.IntStream;
import vmm.algs.DCTWPredictor;

/**
 *
 * @author Rob
 */
public class CounterpointGenerator {
    
    // our VMM and Markov Chain objects
    static DCTWPredictor harmonyModel;
    static MarkovChain melodyModel; 
    static List<MarkovChain> constraint;
    
    static String tonality; // major/minor
    
    static byte[][] inputNotes; // user inputted notes
    
    static byte[] noteSequence; // out counterpoint
    static byte[] choices; // the choices given the register (bass/soprano)
    
    public static byte[] noteToMidi(byte[] noteSequence){
        //byte offset = (byte)(root+(noteSequence[0]<1?48:36));
            for(int j = 1; j < noteSequence.length-1; j++)
                noteSequence[j] += (noteSequence[j]<1?48:36);
        return noteSequence;
    }
    
    public static int chooseNoteIndex(double[] probabilityArray){
        //Searches the array given a randomized key. Returns -1 if nothing found.
        int nextInd = Arrays.binarySearch(probabilityArray, Math.random());
        if(nextInd < 0)
            nextInd = -1-nextInd;
        return nextInd;
    }
    
    public static byte[] createRhythm(byte[][] input, int species){
        byte[] rhythmIn = new byte[input.length];
        for(int i = 0; i < input.length; i++)
            rhythmIn[i] = input[i][1];
        if(species == 1)        // first species = identical rhythm to input
            return rhythmIn;
        else if(species < 4){   // second species = divide by two, third species = divide by four
            ArrayList<Byte> rhythmSoFar = new ArrayList<>();
            double avg = IntStream.range(0,input.length-1).mapToDouble(i -> (double)rhythmIn[i]).average().getAsDouble();
            byte unitIn = (byte)Math.pow(2,Math.round(Math.log(avg)/Math.log(2)));
            byte unitOut = (byte)(unitIn/(species==2?2:4));
            for(int i = 0; i < input.length-1; i++)
                for(int j = 0; j < rhythmIn[i]/unitOut; j++)
                    rhythmSoFar.add(unitOut);
            rhythmSoFar.add(rhythmIn[input.length-1]);
            byte[] ret = new byte[rhythmSoFar.size()];
            for(int i = 0; i < ret.length; i++)
                ret[i] = rhythmSoFar.get(i);
            return ret;
        }
        else                    // treat any other input as first species
            return rhythmIn;
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
    
    public static void printStats(byte[] noteSequence, String testHarmony, String score){
        System.out.println("noteSequence " + ": " + Arrays.toString(noteSequence));
        System.out.println("testHarmony " + ": " + testHarmony);
        System.out.println("evaluation " + ": " + score);        
    }
    
    public static void initialGenerator(int testNum, int noteNum, byte[][] inputNotes){
        byte root = 0;
        //Generate 10 Test patterns using only the markov chain
        for(int i = 0; i < testNum; i++){
            noteSequence = MarkovChain.getSeqFromConstraints(constraint);
            //byte offset = (byte)(root+(noteSequence[0]<1?48:36));
            //noteSequence = noteToMidi(noteSequence);           
            
            byte[] harmony = new byte[noteNum];
            for(int j = 0; j < noteNum; j++)
                harmony[j] = (byte)(inputNotes[j][0]-noteSequence[j]);
            
            String harmonyTS = Arrays.toString(harmony);
            String testHarmony = harmonyTS.substring(1,harmonyTS.length()-1).replace(",", ":4")+":4 !";
            String score = "" + (harmonyModel.logEval(testHarmony, "! ")/testHarmony.length());
            
            printStats(noteSequence, testHarmony, score);
            System.out.println();
        }
        
    }
    
    public static void secondGenerator(int testNum, int noteNum){
        //Generate 10 test patterns using the markov chain and vmm.
        byte root = 0;
        for(int i = 0; i < 10; i++){
            noteSequence = new byte[noteNum+2];
            byte[] actualChoices = Arrays.copyOf(choices, choices.length);
            //note: choices is all notes in the range of the input file (bass/soprano).
            
            //adds 48 or 36 to choices depending on the register (less than C).
            for(int j = 0; j < choices.length; j++)
                actualChoices[j] += (choices[j]<1?48:36)+root;
            //System.out.println("actualChoices2 " + i + ": " + Arrays.toString(actualChoices));
            
            byte offset = root;
            noteSequence[0] = 127; // start sequence with "!"
            String harmonySequence = "! "; //more descriptive
            
            for(int j = 1; j < noteNum+1; j++){
                
                int x = j;                
                double[] harmProbs = new double[choices.length];
                // currentProbs: list of transitions from a given note in the Markov Chain.
                double[] currentProbs = Arrays.copyOf(constraint.get(j-1).getProbs(noteSequence[j-1]), choices.length);                
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
                noteSequence[j] = choices[nextInd];
                harmonySequence += Byte.toString(currentHarms[nextInd])+":4 ";
                
                // figures out the register from the first note in the sequence.
                if(j == 1){
                    offset = (byte)(actualChoices[nextInd]-noteSequence[j]);
                    for(int k = 0; k < choices.length; k++)
                        actualChoices[k] = (byte)(choices[k]+offset);
                }
            }
            harmonySequence += "!";
            //System.out.println("noteSequence " + ": " + Arrays.toString(noteSequence));
            for(int j = 1; j < noteNum+1; j++)
                noteSequence[j] += offset;

            noteSequence[noteSequence.length-1] = 127; //adding the second !
            String score = "" + harmonyModel.logEval(harmonySequence.substring(2), "! ")/(harmonySequence.length()-2);          
            printStats(noteSequence, harmonySequence, score);
            System.out.println();
        }
        
    }
    
    // the original init for quick running.
    public static int testInit() throws FileNotFoundException{
        
        /*****************************************
        * parses and initializes a test melody.
        */
        String testMelody = "60:4 62:4 61:4 63:4 65:4 68:4 67:4 71:4 72:4";
        //byte root = 0;
        tonality = "minor";
        //inputNotes: length 9 x 2 (44-36+1=9)
        inputNotes = new byte[testMelody.length()-testMelody.replace(" ", "").length()+1][2];
        
        int noteNum = inputNotes.length;
        
        Scanner inputParser = new Scanner(testMelody);
        for(int i = 0; inputParser.hasNext(); i++){
            String currentNote = inputParser.next();
            //NOTE is substring from start to indexOf(:), RHYTHM is index(:)+1 to end.
            inputNotes[i][0] = Byte.parseByte(currentNote.substring(0,currentNote.indexOf(':')));
            inputNotes[i][1] = Byte.parseByte(currentNote.substring(currentNote.indexOf(':')+1));
        }
                
        harmonyModel = BachAnalysis.harmonyModel(tonality);
        melodyModel = DataParser.melodyModel("bass", tonality);
        
        //choices: all note choices given the input type (bass/soprano)
        choices = Arrays.copyOf(melodyModel.getLabels(), melodyModel.dim()-1);
        //System.out.println("The choices are: " + Arrays.toString(choices));
        
        // Stupid are the values -1 to -8, and 9. Dumb is an array of max values 127.
        int[] stupid = new int[noteNum];
        Arrays.setAll(stupid, i -> (i+1)*(i<8?-1:1));
        byte[] dumb = new byte[noteNum];
        Arrays.fill(dumb, Byte.MAX_VALUE);
        
        //System.out.println("The stupid is: " + Arrays.toString(stupid));
        //System.out.println("The dumb is: " + Arrays.toString(dumb));
        
        constraint = melodyModel.induceConstraints(noteNum+1,stupid,dumb);
        
        return noteNum;
    }
    
    public static int globalInit(String inputMelody, String scale, String register) throws FileNotFoundException{
        
        /*****************************************
        * parses and initializes a test melody.
        */
        tonality = scale;
        //inputNotes: length 9 x 2 (44-36+1=9)
        inputNotes = new byte[inputMelody.length()-inputMelody.replace(" ", "").length()+1][2];
        
        int noteNum = inputNotes.length;
        
        Scanner inputParser = new Scanner(inputMelody);
        for(int i = 0; inputParser.hasNext(); i++){
            String currentNote = inputParser.next();
            inputNotes[i][0] = Byte.parseByte(currentNote.substring(0,currentNote.indexOf(':')));
            inputNotes[i][1] = Byte.parseByte(currentNote.substring(currentNote.indexOf(':')+1));
        }
                
        harmonyModel = BachAnalysis.harmonyModel(tonality);
        melodyModel = DataParser.melodyModel(register, tonality);
        
        //choices: all note choices given the input type (bass/soprano)
        choices = Arrays.copyOf(melodyModel.getLabels(), melodyModel.dim()-1);
        
        // Stupid are the values -1 to -8, and 9. Dumb is an array of max values 127.
        int[] stupid = new int[noteNum];
        Arrays.setAll(stupid, i -> (i+1)*(i<8?-1:1));
        byte[] dumb = new byte[noteNum];
        Arrays.fill(dumb, Byte.MAX_VALUE);
        
        constraint = melodyModel.induceConstraints(noteNum+1,stupid,dumb);
        
        return noteNum;
    }
    
    public static void main(String[] args) throws FileNotFoundException{
        
        int noteNum;
        Scanner input = new Scanner(System.in);
        System.out.print("please input a melody or 'test': ");
        String inputMelody = input.nextLine();
        if(inputMelody.equals("test")){
            noteNum = testInit();
        }else{
        
            System.out.print("is this major or minor? ");
            String scale = input.nextLine();
            while(!(scale.equals("major") || scale.equals("minor"))){
                System.out.print("sorry, I need major/minor: ");
                scale = input.nextLine();
            }
                
            System.out.print("should the counterpoint be above or below your melody? ");
            String voice = input.nextLine();       
            while(!(voice.equals("above") || voice.equals("below"))){
                System.out.print("sorry, I need above/below: ");
                voice = input.nextLine();
            }
            if (voice.equals("above")){
                voice = "soprano";
            }else{
                voice = "bass";
            }
        
            noteNum = globalInit(inputMelody, scale, voice);
        }
        //initialGenerator(10, noteNum);
        //System.out.println();
        
        secondGenerator(10, noteNum);
        System.out.println();
        
        
    }
}
