/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package counterpoint;

import java.util.*;
import java.io.*;
import java.util.stream.IntStream;
/**
 *
 * @author newma
 */
public class DataParser {
    
    public static void markovChain(double[][] transition){
        int N = 7;                        // number of states
        int state = N - 1;                // current state
        int steps = 0;                    // number of transitions

      // run Markov chain
        while (state > 0) {
            System.out.println(state);
            steps++;
            double r = Math.random();
            double sum = 0.0;

         // determine next state
            for (int j = 0; j < N; j++) {
                sum += transition[state][j];
                if (r <= sum) {
                state = j;
                break;
                }
            }
        }

       System.out.println("The number of steps =  " + steps);
    }
    
    public static double[][] transitionBuilder(byte[] data, int dims, int offset){
        double[][] transition = new double[dims][dims];
        for(int i = 0; i < dims; i++)
            Arrays.fill(transition[i], 0.0);
        for(int i = 0; i < data.length-1; i++){
            byte from = data[i];
            byte to = data[i+1];
            //System.out.println("from: "+(from==Byte.MAX_VALUE?"!":Byte.toString(from))+
            //                   " to: "+(to==Byte.MAX_VALUE?"!":Byte.toString(to)));
            if(from == Byte.MAX_VALUE)
                from = (byte)(dims-1);
            else
                from += offset;
            if(to == Byte.MAX_VALUE)
                to = (byte)(dims-1);
            else
                to += offset;
            //System.out.println(from+" "+to);
            transition[from][to]++;
        }
        for(int i = 0; i < dims; i++){
            double sum = Arrays.stream(transition[i]).sum();
            if(sum > 0)
                for(int j = 0; j < dims; j++)
                    transition[i][j] /= sum;
            System.out.println(Arrays.toString(transition[i]).replace(", ", "\t").replace("]","").substring(1));
        }
        return transition;
    }
    
    public static double[][] transitionBuilder(int dims, int offset, String data){
        //we know from max/min that we have 30 chars to choose from.
        //max is 14 and min is -15. For simplicity, 30 is '!'
        double [][] transition = new double[dims][dims];
        for (int i = 0; i < transition.length; i++){
            for (int j = 0; j < transition[i].length; j++){
                transition[i][j] = 0;
            }
        }
        
        int from = 0;
        int to = 0;
        Scanner notes = new Scanner(data);
        String noteFrom = null;
        String noteTo = null;
        
        System.out.println(offset);
        while(notes.hasNext()){//not correct right now.
            //System.out.println("from: " + data[i] + " to: " + data[i+1]);
            noteFrom = notes.next();
            if (noteFrom.equals('!')){ //first note is !
                noteTo = notes.next();
                if (noteTo.equals('!')){//handle ! cases first.
                    transition[dims-1][dims-1] += 1;
                }else{//case for numbers
                    to = Integer.parseInt(noteTo) + offset;
                    transition[dims-1][to] += 1;
                }
            }
            try {//try to parse the token as an int.
                from = Integer.parseInt(noteFrom);
                from += offset;
                noteTo = notes.next(); //if from is int, parse to.
                if (noteTo.equals('!')){//handle ! cases first.
                    transition[from][dims-1] += 1;
                }else{//numbers case.
                    to = Integer.parseInt(noteTo) + offset;
                    System.out.println("from: " + (from-offset) + " to: " + (to-offset));
                    transition[from][to] += 1;
                }
            } catch( NumberFormatException e){
                //if not a number, ignore it.
            }
            
        }
        double sum = 0.0;
        for (int i = 0; i < transition.length; i++){
            sum = 0.0;
            for (int j = 0; j < transition[i].length; j++){
                sum += transition[i][j]; //sum over each row
            }
            if(sum > 0){
                for (int j = 0; j < transition[i].length; j++){
                    transition[i][j] = transition[i][j]/sum; //divide over each row.
                }
            }
        }
        //print matrix
        for (int i = 0; i < transition.length; i++){
            for (int j = 0; j < transition[i].length; j++){
                System.out.print(transition[i][j] + " ");
            }
            System.out.println();
        }
        
        return null;
    }
    
    public static void oldMain(){
        Scanner sc = null;
        Scanner lineReader = null;
        Scanner melodyReader = null;
        
        String l = null;
        String title = null;
        String scale = null;
        String melody = null;
        String rhythm = null;
        // not easy to handle dynamic lists in java. Concat a string instead.
        String majorNotes = null;
        String minorNotes = null;
        String majorRhythm = null;
        String minorRhythm = null;
        int maxCount = -100;
        int minCount = 100;
        int numCount = 0;
        int note = 0;
        
        try{
            sc = new Scanner(new File("data\\bass.txt"));
        }catch(FileNotFoundException e){
            System.out.println("couldn't read file");
            System.exit(-1);
        }
        //sc.useDelimiter("\n");
        sc.useDelimiter(System.getProperty("line.separator"));
        while(sc.hasNext()){
            
            lineReader = new Scanner(sc.nextLine());
            title = lineReader.next();
                //System.out.println("title: " + title + " ");
            scale = lineReader.next();
                //System.out.println("scale: " + scale + " ");
            
            while(lineReader.hasNext()){
                if (scale.equals("major")){
                    melody = lineReader.next();
                    if (melody.equals("!")){
                        majorNotes += melody + " ";
                        //System.out.print("! ");
                    }else {
                        //assume we're of the form "int:int"
                        melodyReader = new Scanner(melody);
                        note = melodyReader.useDelimiter("\\D+").nextInt();
                        rhythm = melodyReader.next();
                        if (melody.contains("-")){
                            note = note * -1;
                        }
                        //System.out.print(note + " ");
                        if (note < minCount){
                            minCount = note;
                        }
                        if (note > maxCount){
                            maxCount = note;
                        }
                        majorNotes += String.valueOf(note) + " ";
                        majorRhythm += rhythm + " ";
                    }
                }else{
                    melody = lineReader.next();
                    if (melody.equals("!")){
                        minorNotes += melody + " ";
                    }else {
                        //assume we're of the form "int:int"
                        melodyReader = new Scanner(melody);
                        note = melodyReader.useDelimiter("\\D+").nextInt();
                        rhythm = melodyReader.next();
                        if (melody.contains("-")){
                            note = note * -1;
                        }
                        //System.out.print(note + " ");
                        if (note < minCount){
                            minCount = note;
                        }
                        if (note > maxCount){
                            maxCount = note;
                        }
                        minorNotes += String.valueOf(note) + " ";
                        minorRhythm += rhythm + " ";
                    }
                }
                numCount++;  
            }
            //System.out.println();
        }
        System.out.println("max: " + maxCount + " min: " + minCount);
        //char [] ch = majorNotes.toCharArray();
        double [][] matrix = null;
        int dims = maxCount + (minCount * -1) + 1;
        matrix = transitionBuilder(dims, (-1*minCount), majorNotes);
    }
    
    public static byte[][] parseNoteStream(String stream){
        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
        * stream is the training data in String format. This function parses
        * the string of data into notes and their durations using the colon
        * as an index separator.
        * 
        * returns void. pitches[] and lengths[] are edited by address.
        */
        
        byte[] pitches = new byte[stream.length()-stream.replace(" ", "").length()+1];
        byte[] lengths = new byte[pitches.length];
        Scanner noteSc = new Scanner(stream).useDelimiter(" ");
        String currentNote;
        
        for(int ind = 0; noteSc.hasNext(); ind++){
            currentNote = noteSc.next();
            if(currentNote.equals("!")){
                pitches[ind] = Byte.MAX_VALUE;
                lengths[ind] = 0;
            }
            else{
                int colon = currentNote.indexOf(':');
                pitches[ind] = Byte.parseByte(currentNote.substring(0,colon));
                lengths[ind] = Byte.parseByte(currentNote.substring(colon+1));
            }
        }
        byte[][] ret = {pitches, lengths};
        return ret;
    }
    
    public static void parseNoteStream(String stream, byte[] pitches, byte[] lengths, byte[] bounds){
        Scanner noteSc = new Scanner(stream).useDelimiter(" ");
        String currentNote;
        for(int ind = 0; noteSc.hasNext(); ind++){
            currentNote = noteSc.next();
            if(currentNote.equals("!")){
                pitches[ind] = Byte.MAX_VALUE;
                lengths[ind] = 0;
            }
            else{
                int colon = currentNote.indexOf(':');
                pitches[ind] = Byte.parseByte(currentNote.substring(0,colon));
                lengths[ind] = Byte.parseByte(currentNote.substring(colon+1));
                if(pitches[ind] < bounds[0])
                    bounds[0] = pitches[ind];
                else if(pitches[ind] > bounds[1])
                    bounds[1] = pitches[ind];
            }
        }
    }
    
    public static MarkovChain melodyModel(String voice, String mode) throws FileNotFoundException{
        File f = new File("data\\"+voice+".txt");
        Scanner sc = new Scanner(f,"ISO-8859-1");
        //sc.useDelimiter("\n");
        sc.useDelimiter(System.getProperty("line.separator"));
        String train = "!";
        while(sc.hasNext()){
            
            String l = sc.next();
            String thisMode = l.substring(l.indexOf(" m")+1,l.indexOf("or "));
            String notes = l.substring(l.indexOf('!')+1);
            if(mode.toLowerCase().startsWith(thisMode))
                train += notes;
        }
        byte[] pitches = parseNoteStream(train)[0];
        
        return new MarkovChain(pitches);
    }
    
    public static int[] onsets(byte[] lengths){
        int[] ret = new int[lengths.length];
        ret[0] = 0;
        for(int i = 1; i < ret.length; i++)
            ret[i] = ret[i-1]+lengths[i];
        return ret;
    }
    
    public static int getUnit(byte[] rhythm){
        int minLen = 99;
        for(int i = 0; i < rhythm.length; i++)
            minLen = Math.min(minLen, (byte)rhythm[i]);
        return (int)Math.pow(2,IntStream.range(0,rhythm.length-1).mapToDouble(i -> Math.log(rhythm[i])/Math.log(2)).average().getAsDouble());
    }
    
    // returns a Markov chain representing how Bach typically breaks down "units" 
    public static MarkovChain rhythmModel(String part) throws FileNotFoundException{
        File f = new File("data\\"+part+".txt");
        Scanner sc = new Scanner(f,"ISO-8859-1");
        //sc.useDelimiter("\n");
        sc.useDelimiter(System.getProperty("line.separator"));
        ArrayList<Byte> data = new ArrayList<>();
        data.add(Byte.MAX_VALUE);
        while(sc.hasNext()){
            String l = sc.next();
            String notes = l.substring(l.indexOf('!')+2,l.length()-2);
            byte[] rhythm = parseNoteStream(notes)[1];
            // unit needs to be something divisible by 4
            int unit = Math.max(getUnit(rhythm),4);
            int[] onsets = onsets(rhythm);
            int total = onsets[onsets.length-1]+rhythm[rhythm.length-1];
            // if the total length isn't divisible by our unit, assume there's a pickup
            int pickup = unit-total%unit;
            // this isn't totally functional since many pieces leave out the length of the pickup at the end
            pickup = pickup==unit?0:pickup;
            for(int i = 0, current = -1*pickup; i < (total+pickup)/unit; i++){
                // each byte (actually half bytes as the maximum is 15) in the sequence represents how that unit is broken into four
                byte next = 0;
                // ones represent note onsets
                for(int j = 0; j < 4; current += unit/4, j++){
                    if(Arrays.binarySearch(onsets, current) >= 0)
                        next += Math.pow(2, 3-j);
                }
                data.add(next);
            }
            // max value once again represents beginning/end
            data.add(Byte.MAX_VALUE);
        }
        byte[] train = new byte[data.size()];
        IntStream.range(0, train.length).forEach(i -> train[i] = data.get(i));
        return new MarkovChain(train);
    }
    
    public static void main(String args[]) throws FileNotFoundException{
        File f = new File("data\\bass.txt");
        Scanner sc = new Scanner(f,"ISO-8859-1");
        sc.useDelimiter("\n");
        //sc.useDelimiter(System.getProperty("line.separator"));
        HashMap<String, String> majorPieces = new HashMap<>();
        HashMap<String, String> minorPieces = new HashMap<>();
        String trainMajor = "!";
        String trainMinor = "!";
        String l,piece,mode,notes;
        while(sc.hasNext()){
            l = sc.next();
            piece = l.substring(0,l.indexOf(' '));
            mode = l.substring(l.indexOf(" m")+1,l.indexOf("or "));
            notes = l.substring(l.indexOf('!')+1);
            if(mode.equals("maj")){
                trainMajor += notes;
                majorPieces.put(piece, "!"+notes);
            }else{
                trainMinor += notes;
                minorPieces.put(piece, "!"+notes);
            }
        }
        byte[] majorPitches = new byte[trainMajor.length()-trainMajor.replace(" ", "").length()+1];
        byte[] majorLengths = new byte[majorPitches.length];
        byte[] minorPitches = new byte[trainMinor.length()-trainMinor.replace(" ", "").length()+1];
        byte[] minorLengths = new byte[minorPitches.length];
        byte[] bounds = {0,0};
        parseNoteStream(trainMajor, majorPitches, majorLengths, bounds);
        parseNoteStream(trainMinor, minorPitches, minorLengths, bounds);
        
        System.out.println("max: " + bounds[1] + " min: " + bounds[0]);
        int dims = bounds[1]-bounds[0]+2;
        System.out.println(dims);
        MarkovChain majorMarkov = new MarkovChain(majorPitches);
        MarkovChain minorMarkov = new MarkovChain(minorPitches);
    }
}
