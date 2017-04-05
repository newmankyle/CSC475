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
    
    public double getProb(byte from, byte to){
        int indF = Arrays.binarySearch(labels, from);
        int indT = Arrays.binarySearch(labels, to);
        if(Math.min(indF, indT) < 0)
            throw new InvalidParameterException("asked Markov chain for value that isn't present");
        return transitions[indF][indT];
    }
    
    public byte getRandom(byte from){
        double[] probs = getProbs(from);
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
    * @param unary: The unary constraints as an L x 2 matrix. unary[i][0] are positions, unary[i][1] are values.
    * Positive positions indicate that the associated value must be present at the given position, 
    * while negative values indicate that the associated value must not be present
    * at position -(points[i]+1).
    * @param binary: The binary constraints as an L x 3 matrix. Each matrix is a triple (i, j, k)
    * stating that the chain cannot go from j to k between states i and i+1.
    * @return a list of Markov chains representing the constrained Markov process.
    */
    public List<MarkovChain> induceConstraints(byte length, byte[][] unary, byte[][] binary){
        int dim = labels.length;
        boolean[][] allowed = new boolean[length][dim];
        double[][][] modMatrices = new double[length][dim][dim];
        for(int i = 0; i < length; i++){
            Arrays.fill(allowed[i], true);
            for(int j = 0; j < dim; j++)
                modMatrices[i][j] = Arrays.copyOf(transitions[j], dim);
        }
        // processing the constraints
        for(int i = 0; i < unary.length; i++){
            int ind = Arrays.binarySearch(labels, unary[i][1]);
            int step = Math.max(unary[i][0], -1-unary[i][0]);
            for(int j = 0; j < dim; j++)
                if((unary[i][0]>=0 && ind!=j) || (unary[i][0]<0 && ind==j))
                    allowed[step][j] = false;
        }
        for(int i = 0; i < binary.length; i++){
            int step = binary[i][0];
            int indF = Arrays.binarySearch(labels, binary[i][1]);
            int indT = Arrays.binarySearch(labels, binary[i][2]);
            modMatrices[step][indF][indT] = 0;
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
    
    public List<MarkovChain> induceConstraints(int length, byte[][] unary, byte[][] binary){
        if(length >= 128 || length <= 0)
            throw new IllegalArgumentException("Cannot form constraint process with that length.");
        else
            return induceConstraints((byte)length, unary, binary);
    }
    
    public List<MarkovChain> induceConstraints(int length, byte[][] unary){
        if(length >= 128 || length <= 0)
            throw new IllegalArgumentException("Cannot form constraint process with that length.");
        else{
            byte[][] binary = new byte[0][];
            return induceConstraints((byte)length, unary, binary);
        }
    }
    
    public List<MarkovChain> induceConstraints(int length, int[] points, byte[] vals){
        byte[][] unary = new byte[points.length][2];
        for(int i = 0; i < points.length; i++){
            if(points[i] < -128 || points[i] >= 128)
                throw new IllegalArgumentException("Cannot form constraint process with that length.");
            else
                unary[i][0] = (byte)points[i];
            unary[i][1] = vals[i];
        }
        return induceConstraints(length, unary);
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
    
    public static double getProbOfSeq(List<MarkovChain> mc, byte[] sequence){
        double ret = 1.0;
        for(int i = 0; i < mc.size(); i++)
            ret *= mc.get(i).getProb(sequence[i], sequence[i+1]);
        return ret;
    }
    
    public static void main(String[] args){
        double[][] probs = {{0.5,0.25,0.25,0.0},
                            {0.5,0.0,0.5,0.0},
                            {0.5,0.25,0.25,0.0},
                            {0.5,1.0/6,1.0/3,0.0}};
        byte[] notes = {60,62,64,127};
        MarkovChain test = new MarkovChain(notes,probs);
        byte[][] constraint = {{3,62}};
        byte[][] binary = {{1,62,64}};
        List<MarkovChain> testCon = test.induceConstraints(4, constraint, binary);
        for(int i = 0; i < 4; i++){
            MarkovChain mc = testCon.get(i);
            if(i == 0)
                System.out.println(Arrays.toString(mc.transitions[3]));
            else
                for(int j = 0; j < 3; j++)
                    System.out.println(Arrays.toString(mc.transitions[j]));
            System.out.println();
        }
        byte[] test1 = {127,60,60,60,62};
        byte[] test2 = {127,64,64,60,62};
        System.out.println(getProbOfSeq(testCon,test1)/getProbOfSeq(testCon,test2));
    }
}
