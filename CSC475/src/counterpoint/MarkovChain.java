/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package counterpoint;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple implementation of Markov chains which allow for up to 256 states.
 * @author Rob
 */
public class MarkovChain {
    byte[] labels;
    double[][] transitions;
    
    public MarkovChain(byte[] data){
        // determine label set
        byte[] copy = Arrays.copyOf(data, data.length);
        Arrays.sort(copy);
        int dims = 1;
        for(int i = 1; i < copy.length; i++)
            if(copy[i] != copy[i-1])
                dims++;
        labels = new byte[dims];
        labels[0] = copy[0];
        for(int i = 1, j = 1; i < copy.length && j < labels.length; i++){
            if(copy[i] != copy[i-1]){
                labels[j] = copy[i];
                j++;
            }
        }
        transitions = new double[dims][dims];
        for(int i = 0; i < dims; i++)
            Arrays.fill(transitions[i], 0.0);
        int to = Arrays.binarySearch(labels, data[0]);
        for(int i = 1; i < data.length; i++){
            int from = to;
            to = Arrays.binarySearch(labels, data[i]);
            //System.out.println("from: "+data[i-1]+" to: "+data[i]);
            transitions[from][to]++;
        }
        for(int i = 0; i < dims; i++){
            double sum = Arrays.stream(transitions[i]).sum();
            for(int j = 0; j < dims; j++)
                transitions[i][j] /= sum;
            //System.out.println(Arrays.toString(transitions[i]).replace(", ", "\t").replace("]","").substring(1));
        }
    }
    
    public MarkovChain(byte[] initLabels, double[][] initProbs){
        labels = Arrays.copyOf(initLabels, initLabels.length);
        transitions = Arrays.copyOf(initProbs, initProbs.length);
    }
    
    public int dim(){
        return labels.length;
    }
    
    public byte[] getLabels(){  return labels; }
    
    public boolean contains(byte b){    return Arrays.binarySearch(labels, b) >= 0;}
    
    public double[] getProbs(byte from){
        int ind = Arrays.binarySearch(labels, from);
        if(ind < 0)
            throw new InvalidParameterException("asked Markov chain for value that isn't present");
        return Arrays.copyOf(transitions[ind], this.dim());
    }
    
    public byte getRandom(byte from){
        int ind = Arrays.binarySearch(labels, from);
        if(ind < 0)
            throw new InvalidParameterException("asked Markov chain for value that isn't present");
        double[] probs = Arrays.copyOf(transitions[ind], this.dim());
        for(int i = 1; i < probs.length; i++)
            probs[i] += probs[i-1];
        int retInd = Arrays.binarySearch(probs, Math.random());
        if(retInd > 0)
            return labels[retInd];
        else
            return labels[-1-retInd];
    }
    
    /**
    *  Creates a finite-length series of Markov chains to satisfy the given unary constraints.
    * @param length: The length of the Markov chain.
    * @param points: The indices of the constraints. Positive values indicate that the associated
    * value must be present, while negative values indicate that the associated value must not be present
    * at state -(points[i]+1).
    * @param vals: The values of the constraints.
    */
    public List<MarkovChain> induceConstraints(int length, int[] points, byte[] vals){
        int dim = labels.length;
        boolean[][] allowed = new boolean[length][dim];
        double[][][] modMatrices = new double[length][dim][dim];
        for(int i = 0; i < length; i++){
            Arrays.fill(allowed[i], true);
            for(int j = 0; j < dim; j++)
                modMatrices[i][j] = Arrays.copyOf(transitions[j], dim);
        }
        // processing the constraints
        for(int i = 0; i < points.length; i++){
            int ind = Arrays.binarySearch(labels, vals[i]);
            int step = Math.max(points[i], -1-points[i]);
            for(int j = 0; j < dim; j++)
                if((points[i]>=0 && ind!=j) || (points[i]<0 && ind==j))
                    allowed[step][j] = false;
        }
        double[][] modRows = new double[length][dim];
        // matrix extraction, arc consistency
        for(int i = length-1; i >= 0; i--){
            // set columns representing unwanted states to 0
            for(int j = 0; j < dim; j++)
                for(int k = 0; k < dim; k++)
                    if(!allowed[i][k])
                        modMatrices[i][j][k] = 0.0;
            // arc consistency
            for(int j = 0; j < dim; j++){
                // taking sums for renormalization
                if(i == length-1)
                    modRows[i][j] = Arrays.stream(modMatrices[i][j]).sum();
                else{
                    modRows[i][j] = 0.0;
                    for(int k = 0; k < dim; k++)
                        modRows[i][j] += modRows[i+1][k]*modMatrices[i][j][k];
                }
                // remove states that have become dead ends
                if(Arrays.stream(modMatrices[i][j]).sum() == 0.0)
                    allowed[i-1][j] = false;
            }
        }
        //for(int i = 0; i < length; i++)
            //System.out.println(Arrays.toString(allowed[i]));
        double[][][] finalMatrices = new double[length][dim][dim];
        // renormalizing
        for(int i = length-1; i >= 0; i--){
            for(int j = 0; j < dim; j++){
                for(int k = 0; k < dim; k++){
                    if(modRows[i][j] != 0){
                        finalMatrices[i][j][k] = modMatrices[i][j][k]/modRows[i][j];
                        if(i < length-1)
                            finalMatrices[i][j][k] *= modRows[i+1][k];
                    }
                }
            }
        }
        return Arrays.stream(finalMatrices).map(ds -> new MarkovChain(labels,ds)).collect(Collectors.toList());
    }
    
    public static byte[] getSeqFromConstraints(List<MarkovChain> mc){
        byte[] ret = new byte[mc.size()];
        if(mc.get(0).contains(Byte.MAX_VALUE))
            ret[0] = mc.get(0).getRandom(Byte.MAX_VALUE);
        else
            throw new IllegalArgumentException("I have no idea what you want me to do with this");
        for(int i = 1; i < mc.size(); i++)
            ret[i] = mc.get(i).getRandom(ret[i-1]);
        return ret;
    }
    
    public static void main(String[] args){
        double[][] probs = {{0.0,0.5,1.0/6,1.0/3},
                            {0.0,0.5,0.25,0.25},
                            {0.0,0.5,0.0,0.5},
                            {0.0,0.5,0.25,0.25}};
        byte[] notes = {127,60,62,64};
        MarkovChain test = new MarkovChain(notes,probs);
        int[] last = {3};
        byte[] dee = {62};
        List<MarkovChain> testCon = test.induceConstraints(4, last, dee);
        for(int i = 0; i < 4; i++){
            MarkovChain mc = testCon.get(i);
            if(i == 0)
                System.out.println(Arrays.toString(mc.transitions[0]).substring(6));
            else
                for(int j = 1; j < 4; j++)
                    System.out.println(Arrays.toString(mc.transitions[j]).substring(6));
            System.out.println();
        }
    }
}
