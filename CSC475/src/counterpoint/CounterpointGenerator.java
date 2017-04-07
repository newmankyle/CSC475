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
import org.jfugue.pattern.Pattern;
import org.jfugue.player.Player;
import org.jfugue.theory.Note;
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
    static boolean below;
    static int species;
    
    static byte[][] inputNotes; // user inputted notes
    static int[] onsets; // onsets for the notes listed above
    
    static byte[] noteSequence; // out counterpoint
    static byte[] choices; // the choices given the register (bass/soprano)
    
    public static byte[] noteToMidi(byte[] noteSequence){
        byte offset = (byte)(noteSequence[0]<1?48:36);
        for(int j = 1; j < noteSequence.length-1; j++)
            noteSequence[j] += offset;
        return noteSequence;
    }
    
    public static int chooseNoteIndex(double[] probabilityArray){
        //Searches the array given a randomized key. Returns -1 if nothing found.
        int nextInd = Arrays.binarySearch(probabilityArray, Math.random());
        if(nextInd < 0)
            nextInd = -1-nextInd;
        return nextInd;
    }
    
    public static byte[] createRhythm(int species){
        byte[] rhythmIn = new byte[inputNotes.length];
        for(int i = 0; i < inputNotes.length; i++)
            rhythmIn[i] = inputNotes[i][1];
        if(species == 1)        // first species = identical rhythm to input
            return rhythmIn;
        else if(species < 4){   // second species = divide by two, third species = divide by four
            ArrayList<Byte> rhythmSoFar = new ArrayList<>();
            double avg = IntStream.range(0,inputNotes.length-1).mapToDouble(i -> (double)rhythmIn[i]).average().getAsDouble();
            byte unitIn = (byte)Math.pow(2,Math.round(Math.log(avg)/Math.log(2)));
            byte unitOut = (byte)(unitIn/(species==2?2:4));
            for(int i = 0; i < inputNotes.length-1; i++){
                if(rhythmIn[i] < unitOut)
                    rhythmSoFar.add(rhythmIn[i]);
                else
                    for(int j = 0; j < rhythmIn[i]/unitOut; j++)
                        rhythmSoFar.add(unitOut);
            }
            rhythmSoFar.add(rhythmIn[inputNotes.length-1]);
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
    
    public static void secondGenerator(int testNum, int species){
        byte[] rhythm = createRhythm(species);
        byte[][] outputNotes = new byte[rhythm.length][2];
        int noteNum = outputNotes.length;
        
        for(int i = 0; i < noteNum; i++)
            outputNotes[i][1] = rhythm[i];
        System.out.println(Arrays.toString(onsets));
        System.out.println(Arrays.toString(rhythm));
        
        int[] stupid = new int[noteNum];
        Arrays.setAll(stupid, i -> (i+1)*(i<(noteNum-1)?-1:1));
        byte[] dumb = new byte[noteNum];
        Arrays.fill(dumb, Byte.MAX_VALUE);
        
        constraint = melodyModel.induceConstraints(noteNum+1,stupid,dumb);
        //Generate sample counterpoints using the markov chain and vmm.
        byte root = 0;
        for(int i = 0; i < testNum; i++){
            noteSequence = new byte[noteNum+2];
            byte[] actualChoices = Arrays.copyOf(choices, choices.length);
            //note: choices is all notes in the range of the input file (bass/soprano).
            
            //adds 48 or 36 to choices depending on the register (less than C).
            for(int j = 0; j < choices.length; j++)
                actualChoices[j] += (choices[j]<1?48:36)+(below?0:12)+root;
            //System.out.println("actualChoices2 " + i + ": " + Arrays.toString(actualChoices));
            
            byte offset = root;
            noteSequence[0] = 127; // start sequence with "!"
            String harmonySequence = "! "; //more descriptive
            int currentPos = 0;
            
            for(int j = 1; j < noteNum+1; j++){
                
                int x = j;  // "final or effectively final" blah blah blah
                // currentProbs: list of transitions from a given note in the Markov Chain.
                double[] currentProbs = Arrays.copyOf(constraint.get(j-1).getProbs(noteSequence[j-1]), choices.length);
                // corresponding harmonic choices
                byte[] currentHarms = new byte[choices.length];
                // finding current note in the input
                int inputInd = Arrays.binarySearch(onsets, currentPos);
                if(inputInd < 0)
                    inputInd = -2-inputInd;
                
                // currentHarms: the harmonies between the current inputNote and all choices.
                for(int k = 0; k < choices.length; k++)
                    currentHarms[k] = (byte)((inputNotes[inputInd][0]-actualChoices[k])*(below?1:-1));
                // helper strings
                String partialSequence = harmonySequence;    // "final or effectively final" blah blah blah
                String tail = ":"+Byte.toString(rhythm[x-1])+(x==noteNum?" !":"");
                
                //harmProbs: parses currentHarms into ' harm:dur harm:dur ... ! ' form, then calls call VMM predict.
                //  calculates the probabilities of each currenHarm given the harmony sequence so far (testHarmony).
                double[] harmProbs = new double[choices.length];
                Arrays.setAll(harmProbs, k -> harmonyModel.predict(Byte.toString(currentHarms[k])+tail, partialSequence));
                
                currentProbs = calculateNoteProbabilities(currentProbs, harmProbs);
                
                //Searches the array given a randomized key. Returns -1 if nothing found.
                int nextInd = chooseNoteIndex(currentProbs);
               
                //adds the note and harmony value to the sequences.
                noteSequence[j] = choices[nextInd];
                harmonySequence += Byte.toString(currentHarms[nextInd])+tail;
                if(x<noteNum)
                    harmonySequence += " ";
                
                // figures out the register from the first note in the sequence.
                if(j == 1){
                    offset = (byte)(actualChoices[nextInd]-noteSequence[j]);
                    for(int k = 0; k < choices.length; k++)
                        actualChoices[k] = (byte)(choices[k]+offset);
                }
                currentPos += rhythm[j-1];
            }
            //harmonySequence += "!";
            //System.out.println("noteSequence " + ": " + Arrays.toString(noteSequence));
            for(int j = 1; j < noteNum+1; j++)
                noteSequence[j] += offset;

            noteSequence[noteSequence.length-1] = 127; //adding the second !
            String score = "" + harmonyModel.logEval(harmonySequence.substring(2), "! ")/(harmonySequence.length()-2);          
            printStats(noteSequence, harmonySequence, score);
            if (i == 3){
                playCounterpoint(rhythm);
            }
            
            System.out.println();
        }
        
    }
    public static void convertRhythmsToChar(char[] durations, byte[] rhythm){
        
        for(int i = 0; i < rhythm.length; i++){
            if (rhythm[i] == 1){
                durations[i] = 'w';
            }else if(rhythm[i] == 2){
                durations[i] = 'h';
            }else if(rhythm[i] == 4){
                durations[i] = 'q';
            }else if(rhythm[i] == 8){
                durations[i] = 'i';
            }else if(rhythm[i] == 16){
                durations[i] = 's';
            }
        }
    }
    public static void playCounterpoint(byte[] rhythm){
        String pattern1 = "";
        String pattern2 = "";
        char[] counterDurations = new char[rhythm.length];
        char[] cantusDurations = new char[inputNotes.length];
        
        byte[] inputRhythm = new byte[inputNotes.length];
        for(int i = 0; i < inputNotes.length; i++){
            inputRhythm[i] = inputNotes[i][1];
        }
        
        convertRhythmsToChar(counterDurations, rhythm);
        convertRhythmsToChar(cantusDurations, inputRhythm);
        
        for(int i = 0; i < inputNotes.length; i++){
            pattern1 += Note.getToneString(inputNotes[i][0]) + cantusDurations[i] + " ";
        }
        for(int i = 1; i < noteSequence.length-1; i++){
            pattern2 += Note.getToneString(noteSequence[i]) + counterDurations[i-1] + " ";
        }
        System.out.println(pattern1 + "\n" + pattern2);
        
        Pattern p1 = new Pattern(pattern1).setVoice(0).setInstrument("Piano");
        Pattern p2 = new Pattern(pattern2).setVoice(1).setInstrument("Flute");
        Player player = new Player();
        player.play(p1, p2);
        
        Scanner input = new Scanner(System.in);
        System.out.print("Do you want to play it again? y/n ");
        String answer = input.nextLine();
        while(!answer.equals("n")){
            if (answer.equals("y")){
                player.play(p1, p2);
                System.out.print("Do you want to play it again? ");
                answer = input.nextLine();
            }else if (answer.equals("n")){
                break;
            }else{
                System.out.print("please enter y/n: ");
                answer = input.nextLine();
            }
        }
    }
    
    
    public static void globalInit(String inputMelody, String scale, String register) throws FileNotFoundException{
        
        /*****************************************
        * parses and initializes a test melody.
        */
        tonality = scale;
        //inputNotes: length 9 x 2 (44-36+1=9)
        inputNotes = new byte[inputMelody.length()-inputMelody.replace(" ", "").length()+1][2];
        
        Scanner inputParser = new Scanner(inputMelody);
        for(int i = 0; inputParser.hasNext(); i++){
            String currentNote = inputParser.next();
            inputNotes[i][0] = Byte.parseByte(currentNote.substring(0,currentNote.indexOf(':')));
            inputNotes[i][1] = Byte.parseByte(currentNote.substring(currentNote.indexOf(':')+1));
        }
        onsets = new int[inputNotes.length];
        onsets[0] = 0;
        for(int i = 1; i < onsets.length; i++)
            onsets[i] = onsets[i-1]+inputNotes[i-1][1];
                
        harmonyModel = BachAnalysis.harmonyModel(tonality);
        melodyModel = DataParser.melodyModel(register, tonality);
        below = register.equals("bass");
        
        //choices: all note choices given the input type (bass/soprano)
        choices = Arrays.copyOf(melodyModel.getLabels(), melodyModel.dim()-1);
    }
    
    // the original init for quick running.
    public static void testInit() throws FileNotFoundException{
        globalInit("60:4 64:4 62:4 65:4 64:4 67:4 62:4 59:4 60:8", "major", "bass");
    }
    
    public static void main(String[] args) throws FileNotFoundException{
        String[] acceptable = {"1", "2", "3"};
        Scanner input = new Scanner(System.in);
        System.out.print("please input a melody or 'test': ");
        String inputMelody = input.nextLine();
        if(inputMelody.equals("test")){
            testInit();
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
            globalInit(inputMelody, scale, voice);
        }
        System.out.print("what species (1-3)? ");
        String speciesStr = input.nextLine();
        while(Arrays.binarySearch(acceptable, speciesStr) < 0){
            System.out.print("Invalid input. ");
            speciesStr = input.nextLine();
        }
        //initialGenerator(10, noteNum);
        //System.out.println();
        
        secondGenerator(10, Integer.parseInt(speciesStr));
        System.out.println();
        
        
    }
}
