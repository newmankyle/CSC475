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
    
    static byte[][] inputNotes; // user inputted notes
    static int[] onsets; // onsets for the notes listed above
    static int[] inputKey = {0,0};
    static int unit;
    
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
    
    public static byte[] createRhythm(int species) throws FileNotFoundException{
        byte[] rhythmIn = new byte[inputNotes.length];
        for(int i = 0; i < inputNotes.length; i++)
            rhythmIn[i] = inputNotes[i][1];
        unit = Math.max(DataParser.getUnit(rhythmIn),4);
        if(species <= 1)        // first species = identical rhythm to input. treat anything less than 1 as first as well
            return rhythmIn;
        else if(species < 4){   // second species = divide by two, third species = divide by four
            ArrayList<Byte> rhythmSoFar = new ArrayList<>();
            byte unitOut = (byte)(unit/(species==2?2:4));
            for(int i = 0; i < inputNotes.length-1; i++){
                if(rhythmIn[i] < unitOut)
                    rhythmSoFar.add(rhythmIn[i]);
                else{
                    for(int j = 0; j < rhythmIn[i]/unitOut; j++)
                        rhythmSoFar.add(unitOut);
                    if(rhythmIn[i]%unitOut > 0)
                        rhythmSoFar.add((byte)(rhythmIn[i]%unitOut));
                }
            }
            rhythmSoFar.add(rhythmIn[inputNotes.length-1]);
            byte[] ret = new byte[rhythmSoFar.size()];
            for(int i = 0; i < ret.length; i++)
                ret[i] = rhythmSoFar.get(i);
            return ret;
        }
        else if(species == 4){  // fourth species = syncopation
            ArrayList<Byte> rhythmSoFar = new ArrayList<>();
            int minusLast = IntStream.range(0,inputNotes.length-1).map(i -> (int)rhythmIn[i]).sum();
            rhythmSoFar.add((byte)(unit/2));
            for(int i = 0; i < (minusLast-unit/2)/unit; i++)
                rhythmSoFar.add((byte)unit);
            int soFar = rhythmSoFar.stream().mapToInt(b -> (int)b).sum();
            if(soFar < minusLast)
                rhythmSoFar.add((byte)(minusLast-soFar));
            rhythmSoFar.add(rhythmIn[inputNotes.length-1]);
            byte[] ret = new byte[rhythmSoFar.size()];
            for(int i = 0; i < ret.length; i++)
                ret[i] = rhythmSoFar.get(i);
            return ret;
        }
        else{                   // fifth species: free counterpoint. treat anything > 5 as free as well
            MarkovChain rhythm = DataParser.rhythmModel(below?"bass":"soprano");
            unit = Math.min(unit, DataParser.getRealUnit(rhythmIn));
            int total = onsets[onsets.length-1]+inputNotes[inputNotes.length-1][1];
            // if the total length isn't divisible by our unit, assume there's a pickup
            int pickup = unit-total%unit;
            // needed for later
            int before = 16-(int)Math.pow(2,pickup*4/unit);
            int without = (int)Math.pow(2,pickup*4/unit-1);
            // this isn't totally functional since many pieces leave out the length of the pickup at the end
            pickup = pickup==unit?0:pickup;
            byte[][] conditions = new byte[(total+pickup)/unit+before+without][2];
            int units = conditions.length-before-without;
            // need to prevent the ending symbol from being chosen until... the end
            for(int i = 0; i < units; i++){
                conditions[i][0] = (byte)((i+1)*(i<(units-1)?-1:1));
                conditions[i][1] = Byte.MAX_VALUE;
            }
            // we need the first onset to be exactly in time with the original
            // this deals with states where the first onset would be before the one in the original
            for(int i = 0; i < before; i++){
                conditions[units+i][0] = -1;
                conditions[units+i][1] = (byte)(15-i);
            }
            // this deals with states which don't have an onset with the one in the original
            for(int i = 0; i < without; i++){
                conditions[units+before+i][0] = -1;
                conditions[units+before+i][1] = (byte)i;
            }
            // binary conditions to prevent the two most boring choices
            byte[][] binaries = new byte[2*units-2][3];
            for(int i = 0; i < units-1; i++){
                // no onsets to no onsets
                binaries[2*i][0] = (byte)(i+1);
                binaries[2*i][1] = 0;
                binaries[2*i][2] = 0;
                // and on beat only to no onsets
                binaries[2*i+1][0] = (byte)(i+1);
                binaries[2*i+1][1] = 8;
                binaries[2*i+1][2] = 0;
            }
            List<MarkovChain> constraints = rhythm.induceConstraints(units+1, conditions, binaries);
            // so now we make a compressed rhythmic pattern
            byte[] pattern = MarkovChain.getSeqFromConstraints(constraints);
            // and we have to uncompress it
            ArrayList<Integer> finalOnsets = new ArrayList<>();
            for(int i = 0, current = -1*pickup; i < pattern.length-1; i++){
                for(int j = 0; j < 4; current += unit/4, j++){
                    byte pow = (byte)Math.pow(2,3-j);
                    if((pattern[i]&pow) > 0){
                        finalOnsets.add(current);
                    }
                }
            }
            // force unison with the original at the end
            finalOnsets.removeIf(i -> i >= onsets[onsets.length-1]);
            finalOnsets.add(onsets[onsets.length-1]);
            byte[] ret = new byte[finalOnsets.size()];
            for(int i = 0; i < ret.length-1; i++)
                ret[i] = (byte)(finalOnsets.get(i+1)-finalOnsets.get(i));
            ret[ret.length-1] = inputNotes[inputNotes.length-1][1];
            return ret;
        }
    }
    
    public static byte[][] binaryPitchConstraints(byte[] rhythm){
        byte[][][] constraintsPerStep = new byte[rhythm.length][0][0];
        int[] outputOnsets = DataParser.onsets(rhythm);
        // starting this off by removing all parallel fifths and parallel octaves
        for(int i = 1; i < onsets.length; i++){
            int ind = Arrays.binarySearch(outputOnsets, onsets[i]);
            if(ind > 0){
                // get the current melody note and the one before it
                byte inputNoteBefore = inputNotes[i-1][0];
                byte inputNoteAt = inputNotes[i][0];
                // relative pitch class of the note before
                int check = (inputNoteBefore-inputKey[0])%12;
                int[] needsConstraint = new int[choices.length];
                Arrays.fill(needsConstraint, 0);
                for(int j = 0; j < choices.length; j++){
                    byte choice = choices[j];
                    int intervalAt = (below?(check-choice):(choice-check))%12;
                    if(intervalAt < 0)
                        intervalAt += 12;
                    if(intervalAt == 0 || intervalAt == 7){
                        int indOfParallel = Arrays.binarySearch(choices, (byte)(choice+inputNoteAt-inputNoteBefore));
                        if(indOfParallel >= 0)
                            needsConstraint[j]++;
                    }
                }
                byte[][] constraintsForStep = new byte[Arrays.stream(needsConstraint).sum()][2];
                for(int j = 0, k = 0; j < choices.length; j++){
                    if(needsConstraint[j] > 0){
                        constraintsForStep[k][0] = choices[j];
                        constraintsForStep[k][1] = (byte)(choices[j]+inputNoteAt-inputNoteBefore);
                        k++;
                    }
                }
                constraintsPerStep[ind] = constraintsForStep;
            }
        }
        int totalConstraints = Arrays.stream(constraintsPerStep).mapToInt(arr -> arr.length).sum();
        byte[][] ret = new byte[totalConstraints][3];
        for(int i = 0, count = 0; i < rhythm.length; i++){
            for(int j = 0; j < constraintsPerStep[i].length; j++, count++){
                ret[count][0] = (byte)i;
                ret[count][1] = constraintsPerStep[i][j][0];
                ret[count][2] = constraintsPerStep[i][j][1];
            }
        }
        return ret;
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
    
    public static void initialGenerator(int testNum, int noteNum){
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
    
    public static void secondGenerator(int testNum, int species, boolean tryConsts) throws FileNotFoundException{
        byte[] rhythm = createRhythm(species);
        byte[][] outputNotes = new byte[rhythm.length][2];
        int noteNum = outputNotes.length;
        
        for(int i = 0; i < noteNum; i++)
            outputNotes[i][1] = rhythm[i];
        System.out.println(Arrays.toString(onsets));
        System.out.println(Arrays.toString(rhythm));
        
        // the "stupid" constraints- don't use the ending state until the end of the piece
        byte[][] stupid = new byte[noteNum][2];
        for(int i = 0; i < noteNum; i++){
            stupid[i][0] = (byte)((i+1)*(i<(noteNum-1)?-1:1));
            stupid[i][1] = Byte.MAX_VALUE;
        }
        
        if(tryConsts)
            constraint = melodyModel.induceConstraints(noteNum+1,stupid,binaryPitchConstraints(rhythm));
        else
            constraint = melodyModel.induceConstraints(noteNum+1,stupid);
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
            //System.out.println("actualChoices2 " + i + ": " + Arrays.toString(actualChoices));
            
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
        Player player;
        //player.play(p1, p2);
        
        Scanner input = new Scanner(System.in);
        System.out.print("Do you want to play the music? both/counter/melody/n ");
        String answer = input.nextLine();
        while(!answer.equals("n")){
            if (answer.equals("both")){
                player = new Player();
                player.play(p1, p2);
                System.out.print("Do you want to play it again? both/counter/melody/n: ");
                answer = input.nextLine();
            }else if (answer.equals("n")){
                break;
            }else if (answer.equals("melody")){
                player = new Player();
                player.play(p1);
                System.out.print("Do you want to play it again? both/counter/melody/n: ");
                answer = input.nextLine();
            }else if (answer.equals("counter")){
                player = new Player();
                player.play(p2);
                System.out.print("Do you want to play it again? both/counter/melody/n: ");
                answer = input.nextLine();
            }else {
                System.out.print("please enter both/counter/melody/n: ");
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
        kd = new KeyDetector();
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
    
    public static void binaryTest() throws FileNotFoundException{
        globalInit("68:8 70:8 73:8 75:8 77:8 75:8 70:8 73:8 78:8 77:8 72:8 75:8 73:8 80:8 78:8 68:8 67:8 68:8 77:8 75:8 73:16", "bass");
        byte[] rhythm = createRhythm(1);
        int noteNum = rhythm.length;
        
        // the "stupid" constraints- don't use the ending state until the end of the piece
        byte[][] stupid = new byte[noteNum][2];
        for(int i = 0; i < noteNum; i++){
            stupid[i][0] = (byte)((i+1)*(i<(noteNum-1)?-1:1));
            stupid[i][1] = Byte.MAX_VALUE;
        }
        byte[][] binary = binaryPitchConstraints(rhythm);
        for(int i = 0; i < binary.length; i++)
            System.out.println(Arrays.toString(binary[i]));
        
        System.out.println(Arrays.toString(choices));
        for(int i = 0; i < 2; i++){
            if(i == 0)
                constraint = melodyModel.induceConstraints(noteNum+1,stupid);
            else
                constraint = melodyModel.induceConstraints(noteNum+1,stupid,binary);
            System.out.println(Arrays.toString(constraint.get(0).transitions[choices.length]));
            System.out.println();
            for(int j = 1; j < noteNum+1; j++){
                MarkovChain mc = constraint.get(j);
                for(int k = 0; k < choices.length; k++)
                    System.out.println(Arrays.toString(mc.transitions[k]));
                System.out.println();
            }
        }
    }
    
    public static void main(String[] args) throws FileNotFoundException{
        String[] acceptable = {"1", "2", "3", "4", "5"};
        Scanner input = new Scanner(System.in);
        System.out.print("please input a melody or 'test': ");
        String inputMelody = input.nextLine();
        System.out.print("should the counterpoint be above or below your melody? ");
        String voice = input.nextLine();       
        while(!(voice.equals("above") || voice.equals("below"))){
            System.out.print("sorry, I need above/below: ");
            voice = input.nextLine();
        }
        if (voice.equals("above")){
            voice = "soprano";
            if(inputMelody.equals("test"))
                inputMelody = "56:8 58:8 61:8 63:8 65:8 63:8 58:8 61:8 66:8 65:8 60:8 63:8 61:8 68:8 66:8 56:8 55:8 56:8 65:8 63:8 61:16";
        }else{
            voice = "bass";
            if(inputMelody.equals("test"))
                inputMelody = "68:4 70:4 73:4 75:4 77:4 75:4 70:4 73:4 78:4 77:4 72:4 75:4 73:4 80:4 78:4 68:4 67:4 68:4 77:4 75:4 73:16";
        }
        globalInit(inputMelody, voice);
        System.out.print("what species (1-5)? ");
        String speciesStr = input.nextLine();
        while(Arrays.binarySearch(acceptable, speciesStr) < 0){
            System.out.print("Invalid input. ");
            speciesStr = input.nextLine();
        }
        System.out.print("Do you want to try binary pitch constraints (y/n)? ");
        String tryConsts = input.nextLine();
        while(!(tryConsts.charAt(0) == 'y' || tryConsts.charAt(0) == 'n')){
            System.out.print("Invalid input. ");
            tryConsts = input.nextLine();
        }
        /*initialGenerator(10, noteNum);
        System.out.println();
        */
        
        secondGenerator(10, Integer.parseInt(speciesStr), tryConsts.charAt(0)=='y');
        System.out.println();
        
    }
}
