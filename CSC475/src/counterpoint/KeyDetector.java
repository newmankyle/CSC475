/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package counterpoint;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.IntStream;

/**
 *
 * @author Rob
 */
public class KeyDetector {
    static final double[] C_MAJOR_DIAT = {1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1};
    static final double[] C_MAJOR_K_K = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    static final double[] C_MAJOR_TEMP = {0.748, 0.060, 0.488, 0.082, 0.670, 0.460, 0.096, 0.715, 0.104, 0.366, 0.057, 0.400};
    static final double[] C_MAJOR_CHEW = {2, 0, 1, 0, 1, 1, 0, 2, 0, 1, 0, 1};
    static final double[] C_MINOR_DIAT = {1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 1};
    static final double[] C_MINOR_K_K = {6.33, 2.68, 3.52, 5.38, 2.6, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};
    static final double[] C_MINOR_TEMP = {0.712, 0.084, 0.474, 0.618, 0.049, 0.460, 0.105, 0.747, 0.404, 0.067, 0.133, 0.330};
    static final double[] C_MINOR_CHEW = {2, 0, 1, 0, 1, 1, 0, 2, 1, 1, 1, 1};
    static final double[][] C_VECTORS = {C_MAJOR_DIAT, C_MAJOR_K_K, C_MAJOR_TEMP, C_MAJOR_CHEW,
                                         C_MINOR_DIAT, C_MINOR_K_K, C_MINOR_TEMP, C_MINOR_CHEW};
    double[][] all_vectors;
    static final String[] NOTE_NAMES = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#/Ab", "A", "Bb", "B"};
    static final String[] MODES = {"major", "minor"};
    
    public KeyDetector(){
        double[] vector_lengths = new double[8];
        for(int i = 0; i < 8; i++)
            vector_lengths[i] = Math.pow(Arrays.stream(C_VECTORS[i]).map(d -> d*d).sum(), 0.5);
        all_vectors = new double[96][12];
        for(int i = 0; i < 96; i++){
            double[] base_vector = C_VECTORS[i%8];
            double length = vector_lengths[i%8];
            int root = i/8;
            Arrays.setAll(all_vectors[i], j -> base_vector[(j+12-root)%12]/length);
        }
    }
    
    public int[] getIndicesOfSmallest(double[] arr, int num){
        int[] smallest = new int[num];
        Arrays.fill(smallest, -1);
        double[] vals = new double[num];
        Arrays.fill(vals, Double.MAX_VALUE);
        for(int i = 0; i < arr.length; i++){
            int place = Math.max(Arrays.binarySearch(vals, arr[i]), -1-Arrays.binarySearch(vals, arr[i]));
            if(place < num){
                //System.out.println(i+", "+Arrays.toString(vals)+", "+arr[i]+", "+place);
                for(int j = num-1; j >= place; j--){
                    if(j == place){
                        smallest[j] = i;
                        vals[j] = arr[i];
                        break;
                    }
                    else{
                        smallest[j] = smallest[j-1];
                        vals[j] = vals[j-1];
                    }
                }
            }
        }
        return smallest;
    }
    
    public int[] detectKey(byte[][] melody){
        double[] melody_vector = new double[12];
        Arrays.fill(melody_vector, 0);
        for(int i = 0; i < melody.length; i++){
            // first two and last two notes are weighted higher
            int weight = Math.max(3-Math.min(i, melody.length-1-i), 1);
            melody_vector[melody[i][0]%12] += melody[i][1]*weight;
        }
        double vector_length = Math.pow(Arrays.stream(melody_vector).map(d -> d*d).sum(), 0.5);
        for(int i = 0; i < 12; i++)
            melody_vector[i] /= vector_length;
        double[] unit_vector = Arrays.copyOf(melody_vector, 12);
        double[] dists = new double[96];
        for(int i = 0; i < 96; i++){
            double[] compare = all_vectors[i];
            dists[i] = Math.pow(IntStream.range(0,12).mapToDouble(j -> Math.pow(compare[j]-unit_vector[j], 2)).sum(), 0.5);
        }
        int[] top_three = getIndicesOfSmallest(dists, 3);
        Arrays.setAll(top_three, i -> top_three[i]/4);
        boolean useOne = top_three[1] == top_three[2];
        int[] ret = {top_three[useOne?1:0]/2, top_three[useOne?1:0]%2};
        return ret;
    }
    
    public static void main(String[] args) throws FileNotFoundException{
        KeyDetector kd = new KeyDetector();
        File f = new File("data\\bassMIDI.txt");
        Scanner sc = new Scanner(f,"ISO-8859-1");
        //sc.useDelimiter("\n");
        sc.useDelimiter(System.getProperty("line.separator"));
        String l,piece,key,notes;
        while(sc.hasNext()){
            l = sc.next();
            piece = l.substring(0,l.indexOf(' '));
            key = l.substring(l.indexOf(' ')+1,l.indexOf("or ")+2);
            notes = l.substring(l.indexOf('!')+1);
            byte[][] notesAsBytes = CounterpointGenerator.parseNotesAsBytes(notes);
            int[] predictedKey = kd.detectKey(notesAsBytes);
            System.out.println(piece+", "+key+", "+NOTE_NAMES[predictedKey[0]]+" "+MODES[predictedKey[1]]);
        }
    }
}
