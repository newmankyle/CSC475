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
    static int[] inputKey = {0,0};
    
    static byte[] noteSequence; // out counterpoint
    static byte[] choices; // the choices given the register (bass/soprano)
    
    static KeyDetector kd;
    
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
            double avg = IntStream.range(0,inputNotes.length-1).mapToDouble(i -> Math.log(rhythmIn[i])/Math.log(2)).average().getAsDouble();
            byte unitIn = (byte)Math.pow(2,Math.round(avg));
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
        else if(species == 4){  // fourth species = syncopation
            ArrayList<Byte> rhythmSoFar = new ArrayList<>();
            double avg = IntStream.range(0,inputNotes.length-1).mapToDouble(i -> Math.log(rhythmIn[i])/Math.log(2)).average().getAsDouble();
            int minusLast = IntStream.range(0,inputNotes.length-1).map(i -> (int)rhythmIn[i]).sum();
            byte unitIn = (byte)Math.pow(2,Math.round(avg));
            rhythmSoFar.add((byte)(unitIn/2));
            for(int i = 0; i < (minusLast-unitIn/2)/unitIn; i++)
                rhythmSoFar.add(unitIn);
            int soFar = rhythmSoFar.stream().mapToInt(b -> (int)b).sum();
            if(soFar < minusLast)
                rhythmSoFar.add((byte)(minusLast-soFar));
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
        int root = inputKey[0];
        double avg = IntStream.range(0, inputNotes.length).mapToDouble(i -> (double)inputNotes[i][0]).average().getAsDouble();
        int base = (int)Math.round((avg-17*(below?1:-1)-root)/12)*12+root;
        
        byte[] best = new byte[1];
        double bestScore = 18.0;
        for(int i = 0; i < testNum; i++){
            noteSequence = new byte[noteNum+2];
            // the starting notes each step in the Markov chain actually corresponds to
            byte[] actualChoices = Arrays.copyOf(choices, choices.length);
            //note: choices is all notes in the range of the input file (bass/soprano).
            
            // determine what starting note each choice corresponds to
            for(int j = 0; j < choices.length; j++){
                actualChoices[j] += base;
                while((inputNotes[0][0]-actualChoices[j])*(below?1:-1) > 31)
                    actualChoices[j] += 12*(below?1:-1);
                while((inputNotes[0][0]-actualChoices[j])*(below?1:-1) < 7)
                    actualChoices[j] -= 12*(below?1:-1);
            }
            System.out.println("actualChoices2 " + i + ": " + Arrays.toString(actualChoices));
            
            byte offset = (byte)root;
            noteSequence[0] = 127; // start sequence with "!"
            String harmonySequence = "! "; //more descriptive
            int currentPos = 0;
            int pieceEnd = onsets[onsets.length-1]+inputNotes[onsets.length-1][0];
            
            for(int j = 1; j < noteNum+1; j++){
                
                int x = j;  // "final or effectively final" blah blah blah
                // currentProbs: list of transitions from a given note in the Markov Chain.
                double[] currentProbs = Arrays.copyOf(constraint.get(j-1).getProbs(noteSequence[j-1]), choices.length);
                
                // finding current note(s) in the input
                int inputInd = Arrays.binarySearch(onsets, currentPos);
                if(inputInd < 0)
                    inputInd = -2-inputInd;
                int lastInd = inputInd+1;
                int endPos = currentPos+rhythm[j-1];
                while(onsets[lastInd-1]+inputNotes[lastInd-1][0] < pieceEnd && onsets[lastInd] < endPos)
                    lastInd++;
                byte[] currentMelody = new byte[lastInd-inputInd];
                int currNotes = currentMelody.length;
                int[] harmRhythm = Arrays.copyOfRange(onsets, inputInd, lastInd);
                for(int k = 0; k < currNotes; k++){
                    currentMelody[k] = inputNotes[inputInd+k][0];
                    harmRhythm[k] = (k==currNotes-1?endPos:harmRhythm[k+1])-(k==0?currentPos:harmRhythm[k]);
                }
                
                // corresponding harmonic choices
                String[] currentHarms = new String[choices.length];
                
                // currentHarms: the harmonies between the current inputNote and all choices.
                for(int k = 0; k < choices.length; k++){
                    currentHarms[k] = "";
                    for(int l = 0; l < currentMelody.length; l++){
                        if(l > 0)
                            currentHarms[k] += " ";
                        byte currentHarm = (byte)((currentMelody[l]-actualChoices[k])*(below?1:-1));
                        currentHarms[k] += Byte.toString(currentHarm)+":"+Integer.toString(harmRhythm[l]);
                        //System.out.println(j+", "+currentMelody[l]+", "+actualChoices[k]+", "+currentHarms[k]);
                    }
                }
                
                // helper strings
                String partialSequence = harmonySequence;    // "final or effectively final" blah blah blah
                String tail = x==noteNum?" !":"";
                
                //harmProbs: parses currentHarms into ' harm:dur harm:dur ... ! ' form, then calls call VMM predict.
                //  calculates the probabilities of each currenHarm given the harmony sequence so far (testHarmony).
                double[] harmProbs = new double[choices.length];
                Arrays.setAll(harmProbs, k -> harmonyModel.predict(currentHarms[k]+tail, partialSequence));
                
                currentProbs = calculateNoteProbabilities(currentProbs, harmProbs);
                
                //Searches the array given a randomized key. Returns -1 if nothing found.
                int nextInd = chooseNoteIndex(currentProbs);
               
                //adds the note and harmony value to the sequences.
                noteSequence[j] = choices[nextInd];
                harmonySequence += currentHarms[nextInd]+tail;
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
            double score = harmonyModel.logEval(harmonySequence.substring(2), "! ")/(harmonySequence.length()-2);          
            printStats(noteSequence, harmonySequence, Double.toString(score));
            
            if(score < bestScore){
                bestScore = score;
                best = noteSequence;
            }
            
            System.out.println();
        }
        
        noteSequence = best;
        playCounterpoint(rhythm);
        
    }
    public static void convertRhythmsToChar(String[] durations, byte[] rhythm){
        // this version accounts for lengths that aren't factors of 2 or are larger than whole notes
        for(int i = 0; i < rhythm.length; i++){
            // conveniently, you can just string together note lengths
            durations[i] = "";
            int temp = (int)rhythm[i];
            while(temp > 0){
                // find largest power of 2 less than what's left, using 2^4 for anything longer than that
                int exp = (int)Math.min(Math.floor(Math.log(temp)/Math.log(2)), 4);
                // add the corresponding letter
                switch(exp){
                    case 0: durations[i] += "s"; break;
                    case 1: durations[i] += "i"; break;
                    case 2: durations[i] += "q"; break;
                    case 3: durations[i] += "h"; break;
                    default: durations[i] += "w";
                }
                temp -= Math.pow(2,exp);
            }
        }
    }
    public static void playCounterpoint(byte[] rhythm){
        String pattern1 = "";
        String pattern2 = "";
        String[] counterDurations = new String[rhythm.length];
        String[] cantusDurations = new String[inputNotes.length];
        
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
        
        Pattern p1 = new Pattern(pattern1).setVoice(0).setInstrument("Violin").setTempo(80);
        Pattern p2 = new Pattern(pattern2).setVoice(1).setInstrument("Piano").setTempo(80);
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
    
    public static byte[][] parseNotesAsBytes(String notes){
        String temp = notes;
        // something remotely resembling a sanity check: make sure first character is a number
        while(temp.charAt(0) != '-' && (temp.charAt(0) < '0' || temp.charAt(0) > '9'))
            temp = temp.substring(temp.indexOf(' ')+1);
        byte[][] ret = new byte[notes.length()-notes.replace(" ", "").length()+1][2];
        
        Scanner inputParser = new Scanner(notes);
        for(int i = 0; inputParser.hasNext(); i++){
            String currentNote = inputParser.next();
            // then stop parsing the string once you hit something that doesn't start with a number or lacks a colon
            if(!currentNote.contains(":") || temp.charAt(0) != '-' && (currentNote.charAt(0) < '0' || currentNote.charAt(0) > '9'))
                break;
            ret[i][0] = Byte.parseByte(currentNote.substring(0,currentNote.indexOf(':')));
            ret[i][1] = Byte.parseByte(currentNote.substring(currentNote.indexOf(':')+1));
        }
        // there's obviously a lot more error-proofing that needs to be done but this is ok for now
        return ret;
    }
    
    public static void globalInit(String inputMelody, String register) throws FileNotFoundException{
        
        /*****************************************
        * parses and initializes a test melody.
        */
        inputNotes = parseNotesAsBytes(inputMelody);
        inputKey = kd.detectKey(inputNotes);
        tonality = inputKey[1]==0?"major":"minor";
        System.out.println(Arrays.toString(inputKey));
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
    
    public static void globalInit(String inputMelody, String scale, String register) throws FileNotFoundException{
        
        /*****************************************
        * parses and initializes a test melody.
        */
        tonality = scale;
        inputNotes = parseNotesAsBytes(inputMelody);
        inputKey = kd.detectKey(inputNotes);
        inputKey[1] = tonality=="major"?0:1;
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
        globalInit("68:8 70:8 73:8 75:8 77:8 75:8 70:8 73:8 78:8 77:8 72:8 75:8 73:8 80:8 78:8 68:8 67:8 68:8 77:8 75:8 73:16", "bass");
    }
    
    public static void main(String[] args) throws FileNotFoundException{
        kd = new KeyDetector();
        String[] acceptable = {"1", "2", "3", "4"};
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
        System.out.print("what species (1-4)? ");
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
