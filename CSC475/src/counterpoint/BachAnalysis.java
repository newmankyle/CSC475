/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package counterpoint;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.stream.IntStream;
import vmm.algs.DCTWPredictor;

/**
 *
 * @author Rob
 */
public class BachAnalysis {
    public static void bestParam(String fileToTest) throws FileNotFoundException{
        File f = new File("data\\"+fileToTest+".txt");
        for(int i = 2; i < 64; i *= 2){
            Scanner sc = new Scanner(f,"ISO-8859-1");
            sc.useDelimiter(System.getProperty("line.separator"));
            double total = 0.0;
            int tested = 0;
            double most_pred = 999.0;
            double least_pred = 0.0;
            String most_pred_name = "";
            String least_pred_name = "";
            while(sc.hasNext()){
                String l = sc.next();
                String piece = l.substring(0,l.indexOf(' '));
                String mode = l.substring(l.indexOf(" m")+1,l.indexOf("or "));
                String notes = l.substring(l.indexOf('!')+2,l.length()-2);
                String train = notes.substring(0,notes.length()/2);
                String test = notes.substring(notes.length()/2);
                DCTWPredictor pred = new DCTWPredictor();
                pred.init(64, i);
                pred.learn(train);
                double result = pred.logEval(test,train)/test.length();
                if(result < most_pred){
                    most_pred = result;
                    most_pred_name = piece;
                }else if(result > least_pred){
                    least_pred = result;
                    least_pred_name = piece;
                }
                total += result;
                tested++;
            }
            System.out.println("D = "+i);
            System.out.println("Average log loss: "+(total/tested));
            System.out.println("Most predictable: "+most_pred_name+", "+most_pred);
            System.out.println("Least predictable: "+least_pred_name+", "+least_pred);
            System.out.println();
        }
    }
    
    public static void pieceStats(String fileToTest) throws FileNotFoundException{
        File f = new File("data\\"+fileToTest+".txt");
        Scanner sc = new Scanner(f,"ISO-8859-1");
        sc.useDelimiter(System.getProperty("line.separator"));
        double[] most_pred = {999.0,999.0};
        double[] least_pred = {0.0,0.0};
        String[] most_pred_names = {"",""};
        String[] least_pred_names = {"",""};
        double[] logloss = new double[289];
        boolean[] isMajor = new boolean[289];
        for(int i = 0; sc.hasNext(); i++){
            String l = sc.next();
            String piece = l.substring(0,l.indexOf(' '));
            String mode = l.substring(l.indexOf(" m")+1,l.indexOf("or "));
            String notes = l.substring(l.indexOf('!')+2,l.length()-2);
            String train = notes.substring(0,notes.length()/2);
            String test = notes.substring(notes.length()/2);
            DCTWPredictor pred = new DCTWPredictor();
            pred.init(64, 16);
            pred.learn(train);
            double result = pred.logEval(test,train)/test.length();
            logloss[i] = result;
            isMajor[i] = mode.equals("maj");
            int ton = isMajor[i]?0:1;
            if(result < most_pred[ton]){
                most_pred[ton] = result;
                most_pred_names[ton] = piece;
            }else if(result > least_pred[ton]){
                least_pred[ton] = result;
                least_pred_names[ton] = piece;
            }
        }
        String[] statGroups = {fileToTest, "major", "minor"};
        for(int i = 0; i < 3; i++){
            double mean,std;
            int x = i;  // fucking effectively final bullshit
            if(i==0){
                mean = Arrays.stream(logloss).filter(d->d>0).average().getAsDouble();
                std = Math.pow(Arrays.stream(logloss).filter(d->d>0).map(d -> Math.pow(d-mean, 2)).average().getAsDouble(), 0.5);
            }
            else{
                mean = IntStream.range(0, 289).filter(j -> isMajor[j]==(x%2==1)).mapToDouble(j->logloss[j]).average().getAsDouble();
                std = Math.pow(IntStream.range(0, 289).filter(j -> isMajor[j]==(x%2==1)).mapToDouble(j->Math.pow(logloss[j]-mean, 2)).average().getAsDouble(), 0.5);
            }
            double mpred = i==0?Math.min(most_pred[0],most_pred[1]):most_pred[i-1];
            String mname = most_pred_names[i==0?(mpred==most_pred[0]?0:1):(i-1)];
            double lpred = i==0?Math.max(least_pred[0],least_pred[1]):least_pred[i-1];
            String lname = least_pred_names[i==0?(lpred==least_pred[0]?0:1):(i-1)];
            System.out.println(statGroups[i]+" stats");
            System.out.println("Average log loss: "+mean);
            System.out.println("Standard deviation: "+std);
            System.out.println("Most predictable: "+mname+", "+mpred);
            System.out.println("Least predictable: "+lname+", "+lpred);
            System.out.println();
        }
    }
    
    public static void corpusStats(String fileToTest) throws FileNotFoundException{
        File f = new File("data\\"+fileToTest+".txt");
        DCTWPredictor predMajor = new DCTWPredictor();
        predMajor.init(64, 16);
        DCTWPredictor predMinor = new DCTWPredictor();
        predMinor.init(64, 16);
        DCTWPredictor predBoth = new DCTWPredictor();
        predBoth.init(64, 16);
        
        System.out.println("Reading...");
        Scanner sc = new Scanner(f,"ISO-8859-1");
        sc.useDelimiter(System.getProperty("line.separator"));
        HashMap<String, String> majorPieces = new HashMap<>();
        HashMap<String, String> minorPieces = new HashMap<>();
        String trainMajor = "!";
        String trainMinor = "!";
        while(sc.hasNext()){
            String l = sc.next();
            String piece = l.substring(0,l.indexOf(' '));
            String mode = l.substring(l.indexOf(" m")+1,l.indexOf("or "));
            String notes = l.substring(l.indexOf('!')+1);
            if(mode.equals("maj")){
                trainMajor += notes;
                majorPieces.put(piece, "!"+notes);
            }else{
                trainMinor += notes;
                minorPieces.put(piece, "!"+notes);
            }
        }
        System.out.println("Training...");
        predMajor.learn(trainMajor);
        predMinor.learn(trainMinor);
        predBoth.learn(trainMajor+trainMinor.substring(1));
        System.out.println("Calculating...");
        String[] majorNames = majorPieces.keySet().stream().toArray(size -> new String[size]);
        double[] majorResults = new double[majorPieces.size()];
        for(int i = 0; i < majorResults.length; i++){
            String notes = majorPieces.get(majorNames[i]);
            String context = notes.substring(0,notes.length()/2);
            String test = notes.substring(notes.length()/2);
            majorResults[i] = predMajor.logEval(test,context)/test.length();
        }
        String[] minorNames = minorPieces.keySet().stream().toArray(size -> new String[size]);
        double[] minorResults = new double[minorPieces.size()];
        for(int i = 0; i < minorResults.length; i++){
            String notes = minorPieces.get(minorNames[i]);
            String context = notes.substring(0,notes.length()/2);
            String test = notes.substring(notes.length()/2);
            minorResults[i] = predMinor.logEval(test,context)/test.length();
        }
        String[] allNames = Arrays.copyOf(majorNames, 289);
        for(int i = 0; i < minorNames.length; i++)
            allNames[majorNames.length+i] = minorNames[i];
        double[] allResults = new double[289];
        for(int i = 0; i < 289; i++){
            String piece = allNames[i];
            String notes = (i<majorNames.length?majorPieces:minorPieces).get(piece);
            String context = notes.substring(0,notes.length()/2);
            String test = notes.substring(notes.length()/2);
            allResults[i] = predBoth.logEval(test,context)/test.length();
        }
        String[] testNames = {"Major", "Minor", "Combined"};
        String[][] pieceNames = {majorNames, minorNames, allNames};
        double[][] results = {majorResults, minorResults, allResults};
        for(int i = 0; i < 3; i++){
            double mean = Arrays.stream(results[i]).average().getAsDouble();
            double std = Math.pow(Arrays.stream(results[i]).map(d -> Math.pow(d-mean, 2)).average().getAsDouble(), 0.5);
            double mpred = Arrays.stream(results[i]).min().getAsDouble();
            double lpred = Arrays.stream(results[i]).max().getAsDouble();
            String mname = "";
            String lname = "";
            for(int j = 0; j < pieceNames[i].length; j++){
                if(results[i][j] == mpred)
                    mname = pieceNames[i][j];
                else if(results[i][j] == lpred)
                    lname = pieceNames[i][j];
                if(Math.min(mname.length(),lname.length()) > 0)
                    break;
            }
            System.out.println(testNames[i]+" stats");
            System.out.println("Average log loss: "+mean);
            System.out.println("Standard deviation: "+std);
            System.out.println("Most predictable: "+mname+", "+mpred);
            System.out.println("Least predictable: "+lname+", "+lpred);
            System.out.println();
        }
    }
    
    public static DCTWPredictor harmonyModel(String mode) throws FileNotFoundException{
        
        DCTWPredictor ret = new DCTWPredictor();
        ret.init(64, 16);
        
        File f = new File("data\\harmony.txt");
        Scanner sc = new Scanner(f,"ISO-8859-1");
        sc.useDelimiter(System.getProperty("line.separator"));
        
        String train = "!";
        while(sc.hasNext()){
            String l = sc.next();
            String thisMode = l.substring(l.indexOf(" m")+1,l.indexOf("or "));
            //notes is the line after the index of the first '!'
            String notes = l.substring(l.indexOf('!')+1);
            if(mode.toLowerCase().startsWith(thisMode))
                train += notes;
        }
        ret.learn(train);
        
        return ret;
    }
    
    public static void main(String[] args) throws FileNotFoundException{
        corpusStats("soprano");
        corpusStats("bass");
        corpusStats("harmony");
    }
}
