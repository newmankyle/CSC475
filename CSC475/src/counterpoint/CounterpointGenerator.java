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
        byte[] choices = Arrays.copyOf(melodyModel.getLabels(), melodyModel.dim()-1);
        System.out.println(Arrays.toString(choices));
        byte[][] stupid = new byte[noteNum][2];
        for(int i = 0; i < noteNum; i++){
            stupid[i][0] = (byte)((i+1)*(i<8?-1:1));
            stupid[i][1] = Byte.MAX_VALUE;
        }
        List<MarkovChain> constraint = melodyModel.induceConstraints(noteNum+1,stupid);
        /*for(int i = 0; i < 10; i++){
            MarkovChain mc = constraint.get(i);
            if(i == 0)
                System.out.println(Arrays.toString(mc.transitions[mc.dim()-1]));
            else
                for(int j = 0; j < mc.dim(); j++)
                    System.out.println(Arrays.toString(mc.transitions[j]).substring(1));
            System.out.println();
        }*/
        for(int i = 0; i < 10; i++){
            byte[] testPattern = MarkovChain.getSeqFromConstraints(constraint);
            byte offset = (byte)(root+(testPattern[0]<1?48:36));
            for(int j = 0; j < noteNum; j++)
                testPattern[j] += offset;
            System.out.println(Arrays.toString(testPattern));
            byte[] harmony = new byte[noteNum];
            for(int j = 0; j < noteNum; j++)
                harmony[j] = (byte)(inputNotes[j][0]-testPattern[j]);
            String harmonyTS = Arrays.toString(harmony);
            String testHarmony = harmonyTS.substring(1,harmonyTS.length()-1).replace(",", ":4")+":4 !";
            System.out.println(testHarmony);
            System.out.println(harmonyModel.logEval(testHarmony, "! ")/testHarmony.length());
        }
        System.out.println();
        for(int i = 0; i < 10; i++){
            byte[] testPattern = new byte[noteNum+1];
            byte[] actualChoices = Arrays.copyOf(choices, choices.length);
            for(int j = 0; j < choices.length; j++)
                actualChoices[j] += (choices[j]<1?48:36)+root;
            byte offset = root;
            testPattern[0] = 127;
            String currentHarmony = "! ";
            for(int j = 1; j < noteNum+1; j++){
                double[] currentProbs = Arrays.copyOf(constraint.get(j-1).getProbs(testPattern[j-1]), choices.length);
                byte[] currentHarms = new byte[choices.length];
                for(int k = 0; k < choices.length; k++)
                    currentHarms[k] = (byte)(inputNotes[j-1][0]-actualChoices[k]);
                double[] harmProbs = new double[choices.length];
                String testHarmony = currentHarmony;    // "final or effectively final" blah blah blah
                int x = j;
                Arrays.setAll(harmProbs, k -> harmonyModel.predict(Byte.toString(currentHarms[k])+":4"+(x==noteNum?" !":""), testHarmony));
                double harmSum = Arrays.stream(harmProbs).sum();
                Arrays.setAll(currentProbs, k -> currentProbs[k]/harmSum*harmProbs[k]);
                double toNormalize = Arrays.stream(currentProbs).sum();
                Arrays.setAll(currentProbs, k -> currentProbs[k]/toNormalize);
                for(int k = 1; k < choices.length; k++)
                    currentProbs[k] += currentProbs[k-1];
                int nextInd = Arrays.binarySearch(currentProbs, Math.random());
                if(nextInd < 0)
                    nextInd = -1-nextInd;
                testPattern[j] = choices[nextInd];
                currentHarmony += Byte.toString(currentHarms[nextInd])+":4 ";
                if(j == 1){
                    offset = (byte)(actualChoices[nextInd]-testPattern[j]);
                    for(int k = 0; k < choices.length; k++)
                        actualChoices[k] = (byte)(choices[k]+offset);
                }
            }
            currentHarmony += "!";
            for(int j = 1; j < noteNum+1; j++)
                testPattern[j] += offset;
            System.out.println(Arrays.toString(testPattern));
            System.out.println(currentHarmony);
            System.out.println(harmonyModel.logEval(currentHarmony.substring(2), "! ")/(currentHarmony.length()-2));
        }
    }
}
