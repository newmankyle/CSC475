from music21 import *
import numpy as np
from sys import argv
choice = int(argv[1])

def highest(n):
    if n.isChord:
        return n[0]
    else:
        return n

def lowest(n):
    if n.isChord:
        return n[-1]
    else:
        return n

def diffs(arr):
    return arr[1:]-arr[:-1]
    
def start(n):
    return int(n.offset*4)
    
def end(n):
    return int((n.offset+n.duration.quarterLength)*4)

for c in corpus.getBachChorales():
    pas = corpus.parse(c).parts
    if len(pas) == 4:
        ck = corpus.parse(c).analyze('key')
        if "302" in c:
            ck = key.Key('D')
        cmode = ck.mode
        cton = ck.tonic.midi%12
        SNotes = map(highest, pas[0].flat.notes)
        BNotes = map(lowest, pas[3].flat.notes)
        piecelen = int(pas.highestTime*4)
        SArr = np.zeros(piecelen+1,dtype=np.int16)
        BArr = np.zeros(piecelen+1,dtype=np.int16)
        for nArr,a in zip([SNotes,BNotes],[SArr,BArr]):
            a[0] = -1
            for n in nArr:
                nStart = start(n)
                nEnd = end(n)
                if "227.11" in c and nStart == 0 and nEnd == 16:
                    nStart += 288
                    nEnd += 288
                a[nStart] = n.pitch.midi
                a[nEnd] = -1
        firstNote = min(np.nonzero(SArr>0)[0][0],np.nonzero(BArr>0)[0][0])
        lastNote = max(np.nonzero(SArr==-1)[0][-1],np.nonzero(BArr==-1)[0][-1])
        SArr = SArr[firstNote:lastNote+1]
        BArr = BArr[firstNote:lastNote+1]
        piecelen = len(SArr)-1
        diff = [SArr[np.nonzero(SArr[0:i+1])[0][-1]]-BArr[np.nonzero(BArr[0:i+1])[0][-1]] for i in range(0,piecelen)]
        rests = []
        continues = []
        restOnsets = []
        for i in range(0,piecelen+1):
            if min(SArr[np.nonzero(SArr[0:i+1])[0][-1]],BArr[np.nonzero(BArr[0:i+1])[0][-1]]) == -1:
                if i == 0 or (len(rests) == 0 or rests[-1] != i-1):
                    restOnsets.append(i)
                rests.append(i)
            elif i == 0 or (len(rests) > 0 and rests[-1] == i-1):
                continues.append(i)
        if restOnsets[0] == 0:
            restOnsets.remove(0)
        allChanges = np.unique(np.concatenate((np.nonzero(SArr),np.nonzero(BArr)),axis=1))
        dddd = diffs(allChanges)
        factor = 1
        if max(dddd) > 16 and min(dddd) > 2:
            factor = min(dddd)/2
        for j,x,y in zip(range(0,len(continues)),continues,restOnsets):
            SPart = SArr[x:y+1]
            BPart = BArr[x:y+1]
            DPart = np.array(diff[x:y+1],dtype=np.int16)
            changes = np.unique(np.concatenate((np.nonzero(SPart),np.nonzero(BPart)),axis=1))
            midis = [SPart,BPart,DPart]
            points = [np.nonzero(SPart)[0],np.nonzero(BPart)[0],changes]
            if max([len(a) for a in points]) >= 8:
                for i,pArr,tArr in zip(range(0,3),midis,points):
                    if i == choice or choice > 2:
                        midis = pArr[tArr[:-1]]
                        if i < 2:
                            midis = midis-cton
                            midis = (midis-round(np.average(midis)/12)*12).astype(np.int16)
                        st = c[c.find("bwv"):c.find(".mxl")]+"-"+str(j)+" "+cmode
                        if x == 0:
                            st += " ! "
                        else:
                            st += " ~ "
                        for midi,dur in zip(midis,diffs(tArr)):
                            st += str(midi)+":"+str(int(dur/factor))+" "
                        if y == piecelen:
                            st += "!"
                        else:
                            st += "~"
                        print(st)