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
#for c in [corpus.getWork("bwv302")]:
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
        if np.count_nonzero(np.concatenate((SArr,BArr))==-1) == 2:
            diff = [SArr[np.nonzero(SArr[0:i+1])[0][-1]]-BArr[np.nonzero(BArr[0:i+1])[0][-1]] for i in range(0,piecelen)]
            changes = np.unique(np.concatenate((np.nonzero(SArr),np.nonzero(BArr)),axis=1))
            dddd = diffs(changes)
            factor = 1
            if max(dddd) > 16 and min(dddd) > 2:
                factor = min(dddd)/2
            midis = [SArr,BArr,np.array(diff,dtype=np.int16)]
            points = [np.nonzero(SArr)[0],np.nonzero(BArr)[0],changes]
            for i,pArr,tArr in zip(range(0,3),[SArr,BArr,np.array(diff,dtype=np.int16)],[np.nonzero(SArr)[0],np.nonzero(BArr)[0],changes]):
                if i == choice or choice > 2:
                    midis = pArr[tArr[:-1]]
                    #if i < 2:
                        #midis = midis-cton
                        #midis = (midis-round(np.average(midis)/12)*12).astype(np.int16)
                    st = c[c.find("bwv"):c.find(".mxl")]+" "+str(ck)+" ! "
                    for midi,dur in zip(midis,diffs(tArr)):
                        st += str(midi)+":"+str(int(dur/factor))+" "
                    st += "!"
                    print(st)