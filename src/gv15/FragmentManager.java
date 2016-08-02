package gv15;

import analysis.PackCreator;
import data.Assembly;
import data.Consensus;
import data.Contig;
import data.IReadManager;
import data.LineData;
import data.Read;
import data.ReadMetaData;
import data.auxiliary.CigarEvent;
import data.auxiliary.CigarFeature;
import data.auxiliary.CigarInsertEvent;
import data.auxiliary.Feature;
import static gv15.Gv15.VariancePos;
import io.AssemblyFile;
import io.AssemblyFileHandler;
import io.TabletFile;
import io.TabletFileHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author ranasi01
 */
public class FragmentManager {
    
    private int maxReadCount;
    
    public FragmentManager(){
        
    }
    
    public void ProcessFragments(HashMap<String,ArrayList<Phenotype>> phenotypes,ReferenceManager referenceManager,
            PanelManager panelManager,int flank) throws Exception{
        
        boolean combine = false;
        for(String type:phenotypes.keySet()){
            //if(type.equals("CIN3")){
            ArrayList<ArrayList<String>> ReferenceDataCollection = new ArrayList();
            ArrayList<Map<String,FragmentNode>[]> FragmentsCollection = new ArrayList();

            ArrayList<String> tempReference = new ArrayList();
            Map<String,FragmentNode>[] tempFragments;

            for(int sampleNo = 0;sampleNo<phenotypes.get(type).size();sampleNo++){
                if(ReferenceDataCollection.size() >= 2){
                    ReferenceDataCollection.clear();
                    FragmentsCollection.clear();

                    ReferenceDataCollection.add(new ArrayList(referenceManager.ReferenceData.get(type)));
                    FragmentsCollection.add(panelManager.GetPanelFromPhenotype(type).Fragments);                    
                }

                String[] filenames  = new String[]{
                    "C:\\Users\\ranasi01\\Documents\\Project\\Data\\" + phenotypes.get(type).get(sampleNo).FileName,
                    "C:\\Users\\ranasi01\\Documents\\Project\\Data\\ref.fasta"
                };

                TabletFile tabletFile;
                tabletFile = TabletFileHandler.createFromFileList(filenames);

                AssemblyFile[] files = tabletFile.getFileList();
                File cacheDir = new File("D:\\");

                AssemblyFileHandler assemblyFileHandler = new 
                    AssemblyFileHandler(files, cacheDir);

                assemblyFileHandler.runJob(0);

                TabletFileHandler.addAsMostRecent(tabletFile);
                Assembly assembly = assemblyFileHandler.getAssembly();

                assembly.getAssemblyStatistics().setAssembly(tabletFile.assembly);
                assembly.getAssemblyStatistics().setReference(tabletFile.assembly);

                //Loading data from contigs
                Contig selectedCotig = assembly.getContig(0);

                //TODO
                int startCoord = VariancePos - flank;
                int endCoord  = VariancePos + flank;

                if(assembly.getBamBam() != null){
                    //Set Location
                    assembly.getBamBam().setSize(endCoord-startCoord);
                    assembly.getBamBam().setBlockStart(selectedCotig, startCoord);

                    selectedCotig.clearCigarFeatures();
                    assembly.getBamBam().loadDataBlock(selectedCotig);           
                    assembly.getBamBam().indexNames();
                }
                //Extracting Referecne Data
                Consensus consensus = selectedCotig.getConsensus();            
                byte[] referenceData = consensus.getRange(startCoord-1, endCoord-1);
                tempReference = new ArrayList();
                for(int i = 0;i<referenceData.length;i++){
                    tempReference.add(UtilityFunctions.getInstance().GetBaseFromVal(referenceData[i]));
                }

                //Sorting Reads
                selectedCotig.getReads().trimToSize();
                Collections.sort(selectedCotig.getReads());
                selectedCotig.calculateOffsets(assembly);

                //Packing Reads
                PackCreator packCreator = new PackCreator(selectedCotig, false);
                packCreator.runJob();

                //Packing Reads               
                IReadManager reads = null;       
                reads = selectedCotig.getPackManager();

                //Extract Insert Features
                ArrayList<Feature> insertFeatures = new ArrayList();            
                int insertCount = 0;
                for(int i= 0;i<selectedCotig.getFeatures().size();i++){
                    Feature currentFeature = selectedCotig.getFeatures().get(i);

                    boolean filter = (currentFeature.getDataPS() > VariancePos - 3
                            && currentFeature.getDataPS() < VariancePos);

                    if(currentFeature.getGFFType().equals("CIGAR-I") &&
                            currentFeature.getDataPS() >= startCoord-2 && 
                            currentFeature.getDataPE() <= endCoord &&
                            filter){
                        insertFeatures.add(currentFeature);

                        if(currentFeature.getDataPS() > VariancePos - 3
                                && currentFeature.getDataPS() < VariancePos - 1)
                            referenceManager.ShiftVal++;

                        //Add the inserted value to the Reference data
                        //and shift the Referecne values
                        int maxInsertions = -1;
                        CigarFeature cf = (CigarFeature)currentFeature;
                        for(CigarEvent e : cf.getEvents()){
                            CigarInsertEvent insEv = (CigarInsertEvent)e;

                            int insertLength = insEv.getInsertedBases().length();
                            if(insertLength>maxInsertions)
                                maxInsertions = insertLength;
                        }

                        int insertStartPos = (currentFeature.getDataPS()+2)-startCoord;

                        for(int insNum = 0;insNum<maxInsertions;insNum++){
                            tempReference.add(insertStartPos+insNum+insertCount, "INS");
                        }

                        insertCount+=maxInsertions;
                    }    
                }

         
                if(phenotypes.get(type).get(sampleNo).FileName.equals("samples\\chr1_871234_871434_DA0061616_IonXpress_001_rawlib.bam"))
                       System.err.println("");
                    
                //Extract Reads
                maxReadCount += reads.size();    
                
                tempFragments = new HashMap[tempReference.size()];

                for(int readNo = 0;readNo<reads.size();readNo++){
                    LineData lineData = reads.getPixelData(readNo, 0+startCoord-1, 15, 1f, true);

                    ReadMetaData[] rmds = lineData.getRMDs();
                    int[] indexes = lineData.getIndexes();
                    Read[] readArr = lineData.getReads();

                    int count = 0;
                    while(rmds[count] == null && count < rmds.length){
                        count++;
                    }
                    ReadMetaData testRMD = rmds[count];

                    int testIndex = 0;
                    int insCount = 0;   
                    HashSet<CigarInsertEvent> addedInsets = new HashSet();
                    for(int colIndex = 0;colIndex<indexes.length;colIndex++){
                        

                       while(tempReference.get(testIndex) == "INS"){
                           int insAmount = 1;
                           for(int insNum = 0;insNum<insertFeatures.size();insNum++){

                                Feature event = insertFeatures.get(insNum);
                                CigarFeature cf = (CigarFeature)event; 
                                    for(CigarEvent e : cf.getEvents()){
                                        Read read = e.getRead();
                                        CigarInsertEvent insEvent = (CigarInsertEvent)e;
                                        if(ReadArrayContainsInsert(read, readArr)){
                                            int insertStartPos = (cf.getDataPS()+2)-startCoord;

                                            int adjustedColIndex = colIndex+insCount; 

                                            if((insertStartPos) == colIndex && !addedInsets.contains(insEvent)){

                                                addedInsets.add(insEvent);
                                                
                                                insAmount = insEvent.getInsertedBases().length();
                                                if(tempFragments[testIndex]==null){
                                                    tempFragments[testIndex] = new HashMap();
                                                    FragmentNode tempNode = new FragmentNode();
                                                    tempNode.ReadCount = 1;

                                                    if(colIndex < indexes.length - 1){
                                                        int nextIndex = ProbeToNextFragment(testIndex,tempReference);
                                                        if(indexes[colIndex+1] != -1){
                                                            tempNode.ConnectedFragments.put(UtilityFunctions.getInstance().GetBaseFromVal(
                                                                    testRMD.getStateAt(indexes[colIndex])), 
                                                                new HashSet<Integer>());
                                                            tempNode.ConnectedFragments.get(UtilityFunctions.getInstance().GetBaseFromVal(
                                                                    testRMD.getStateAt(indexes[colIndex]))).
                                                                    add(nextIndex);
                                                        }
                                                    }
                                                    AddInsertedBases(tempFragments, adjustedColIndex, insEvent.getInsertedBases(), tempNode);
                                                }else if(!tempFragments[testIndex].containsKey(insEvent.getInsertedBases())){
                                                    FragmentNode tempNode = new FragmentNode();
                                                    tempNode.ReadCount = 1;

                                                    if(colIndex < indexes.length - 1){
                                                        int nextIndex = ProbeToNextFragment(testIndex,tempReference);
                                                        if(indexes[colIndex+1] != -1){
                                                            tempNode.ConnectedFragments.put(UtilityFunctions.getInstance().GetBaseFromVal(
                                                                    testRMD.getStateAt(indexes[colIndex])), 
                                                                new HashSet<Integer>());
                                                            tempNode.ConnectedFragments.get(UtilityFunctions.getInstance().GetBaseFromVal(
                                                                    testRMD.getStateAt(indexes[colIndex]))).
                                                                    add(nextIndex);
                                                        }
                                                    } 
                                                    AddInsertedBases(tempFragments, adjustedColIndex, insEvent.getInsertedBases(), tempNode);
                                                }else{
                                                    if(colIndex < indexes.length - 1){
                                                        int nextIndex = ProbeToNextFragment(testIndex,tempReference);
                                                        if(!tempFragments[testIndex].get(insEvent.getInsertedBases()).ConnectedFragments.
                                                                containsKey(UtilityFunctions.getInstance().GetBaseFromVal(
                                                                        testRMD.getStateAt(indexes[colIndex+1])))){
                                                            if(indexes[colIndex+1] != -1){
                                                                tempFragments[testIndex].get(insEvent.getInsertedBases()).ConnectedFragments.
                                                                        put(UtilityFunctions.getInstance().GetBaseFromVal(
                                                                                testRMD.getStateAt(indexes[colIndex])), 
                                                                        new HashSet<Integer>());
                                                                tempFragments[testIndex].get(insEvent.getInsertedBases()).ConnectedFragments.
                                                                        get(UtilityFunctions.getInstance().GetBaseFromVal(
                                                                                testRMD.getStateAt(indexes[colIndex]))).
                                                                        add(nextIndex);
                                                            }
                                                        }
                                                    }
                                                    tempFragments[testIndex].get(insEvent.getInsertedBases()).ReadCount++;
                                                }

                                                //Add to previous
                                                if(colIndex!=0){
                                                    String prevVal = UtilityFunctions.getInstance().GetBaseFromVal(
                                                            testRMD.getStateAt(indexes[colIndex]-1));

                                                    if(tempFragments[testIndex-1].containsKey(prevVal)){
                                                        if(tempFragments[testIndex-1].get(prevVal).ConnectedFragments.
                                                                containsKey(Character.toString(insEvent.getInsertedBases().charAt(0)))){
                                                            tempFragments[testIndex-1].get(prevVal).ConnectedFragments.
                                                                    get(Character.toString(insEvent.getInsertedBases().charAt(0))).
                                                                    add(testIndex);
                                                        }else
                                                        {
                                                            tempFragments[testIndex-1].get(prevVal).ConnectedFragments.
                                                                    put(Character.toString(insEvent.getInsertedBases().charAt(0))
                                                                            , new HashSet<Integer>());
                                                            tempFragments[testIndex-1].get(prevVal).ConnectedFragments.
                                                                    get(Character.toString(insEvent.getInsertedBases().charAt(0))
                                                                    ).add(testIndex);
                                                        }
                                                    }
                                                }

                                            }
                                        }
                                    }
                            }                    
                           testIndex+=insAmount;
                           insCount+=insAmount;           
                       }//End insert handling loop 
                        if(testRMD == null || indexes[colIndex] >= testRMD.length() || indexes[colIndex] == -1){
                            testIndex++;
                            continue;
                        }

                        byte rmdData = testRMD.getStateAt(indexes[colIndex]);
                        String val = UtilityFunctions.getInstance().GetBaseFromVal(rmdData);

                            if(tempFragments[testIndex] == null){
                                tempFragments[testIndex] = new HashMap();
                                FragmentNode tempNode = new FragmentNode();
                                tempNode.ReadCount = 1;

                                if(colIndex < indexes.length - 1){
                                    int nextIndex = ProbeToNextFragment(testIndex,tempReference);
                                    if(indexes[colIndex+1] != -1){
                                        tempNode.ConnectedFragments.put(UtilityFunctions.getInstance().GetBaseFromVal(
                                                testRMD.getStateAt(indexes[colIndex+1])), 
                                                new HashSet<Integer>());
                                        tempNode.ConnectedFragments.get(UtilityFunctions.getInstance().GetBaseFromVal(
                                                testRMD.getStateAt(indexes[colIndex+1]))).
                                                add(nextIndex);
                                    }
                                }
                                tempFragments[testIndex].put(val, tempNode);
                            }else if(!tempFragments[testIndex].containsKey(val)){
                                FragmentNode tempNode = new FragmentNode();
                                tempNode.ReadCount = 1;
                                if(colIndex < indexes.length - 1){
                                    int nextIndex = ProbeToNextFragment(testIndex,tempReference);
                                    if(indexes[colIndex+1] != -1){
                                        tempNode.ConnectedFragments.put(UtilityFunctions.getInstance().GetBaseFromVal(
                                                testRMD.getStateAt(indexes[colIndex+1])), 
                                                new HashSet<Integer>());
                                        tempNode.ConnectedFragments.get(UtilityFunctions.getInstance().GetBaseFromVal(
                                                testRMD.getStateAt(indexes[colIndex+1]))).
                                                add(nextIndex);                               
                                    }
                                }
                                tempFragments[testIndex].put(val, tempNode);
                            }else{
                                 if(colIndex < indexes.length - 1){
                                    int nextIndex = ProbeToNextFragment(testIndex,tempReference);

                                    if(!tempFragments[testIndex].get(val).ConnectedFragments.
                                            containsKey(UtilityFunctions.getInstance().GetBaseFromVal(
                                                    testRMD.getStateAt(indexes[colIndex+1])))){
                                        if(indexes[colIndex+1] != -1){
                                            tempFragments[testIndex].get(val).ConnectedFragments.
                                                    put(UtilityFunctions.getInstance().GetBaseFromVal(
                                                            testRMD.getStateAt(indexes[colIndex+1])), 
                                                    new HashSet<Integer>());
                                            tempFragments[testIndex].get(val).ConnectedFragments.
                                                    get(UtilityFunctions.getInstance().GetBaseFromVal(
                                                            testRMD.getStateAt(indexes[colIndex+1]))).
                                                    add(nextIndex);
                                        }
                                    }else{
        //                                Fragments[testIndex].get(val).ConnectedFragments.
        //                                            get(GetBase(testRMD.getStateAt(indexes[colIndex+1]))).
        //                                        add(nextIndex);  
                                    }
                                }
                                tempFragments[testIndex].get(val).ReadCount++;
                            }

                        testIndex++;
                    }//end looping columns
                }//end reading reads
                ReferenceDataCollection.add(tempReference);
                FragmentsCollection.add(tempFragments);
                //FragmentPrinter(Fragments);

                if(sampleNo>=1){
                    //Combine References  
                    ArrayList<String> combined = referenceManager.
                            CombineReferences(ReferenceDataCollection.get(0), 
                            ReferenceDataCollection.get(1));
                    referenceManager.AddReference(type, combined);
                    
                    //FragmentPrinter(FragmentsCollection.get(0));
                    //FragmentPrinter(FragmentsCollection.get(1));

                    if(sampleNo == 2)
                        System.err.println("");
                    
                    Adjust2(ReferenceDataCollection.get(0), 
                            FragmentsCollection.get(0), referenceManager.ReferenceData.get(type));
                    Adjust2(ReferenceDataCollection.get(1), 
                            FragmentsCollection.get(1), referenceManager.ReferenceData.get(type));
                    
//                    AdjustForInsertions(ReferenceDataCollection.get(1), 
//                            FragmentsCollection.get(1), referenceManager.ReferenceData.get(type));
//                    AdjustForInsertions(ReferenceDataCollection.get(0), 
//                            FragmentsCollection.get(0), referenceManager.ReferenceData.get(type));

                    //Combine Fragments
                    panelManager.GetPanelFromPhenotype(type).Fragments = 
                            CombineFragments(referenceManager.ReferenceData.get(type),
                            FragmentsCollection.get(0), FragmentsCollection.get(1), 
                            ReferenceDataCollection.get(0), ReferenceDataCollection.get(1),
                            referenceManager.ReferenceData.get(type).size());

                    //FragmentPrinter(panelManager.enginePanels.get(0).Fragments);    

                    //if(sampleNo == 2)
                        //break;
                    
                }else{

                    referenceManager.AddReference(type,ReferenceDataCollection.get(0));
                    panelManager.GetPanelFromPhenotype(type).Fragments = FragmentsCollection.get(0);
                    //testPanel2.Fragments = FragmentsCollection.get(0);
                }
                System.err.println("");
            }//End aggregating samples
            
            //}//Type Check
        }
    }
    public void Adjust2(ArrayList<String> newRef,
            Map<String,FragmentNode>[] fragments,ArrayList<String> combinedRef){
        
        int newRefIndex = 0;
        int combinedRefIndex = 0;
        
        ArrayList<String> tempRef = new ArrayList();
        
        //Adjust for insertions    
        for(int index = 0;index<combinedRef.size();index++){
            if(combinedRef.get(combinedRefIndex).equals(
                newRef.get(newRefIndex))){
                tempRef.add(newRef.get(newRefIndex));
                newRefIndex++;
                combinedRefIndex++;
                continue;
            }
            if(combinedRef.get(combinedRefIndex).equals("INS")){
                tempRef.add("*");
                combinedRefIndex++;
            }
        }
        
        int insertionCount = 0;
        newRefIndex = 0;
        for(int index = 0;index<tempRef.size();index++){
            if(tempRef.get(index).equals("*")){
                
                //find how many new insertions were added
                int endIndex = index;
                while(tempRef.get(endIndex)=="*")
                    endIndex++;
                int addedInsertions = endIndex - index;
                
                //Adjust any previous bases 
                for(int prevIndex = 0;prevIndex<index-insertionCount;prevIndex++){
                    for(String baseType:fragments[prevIndex].keySet()){
                       for(String connectedRead:fragments[prevIndex].
                           get(baseType).ConnectedFragments.keySet()){

                               Iterator it=fragments[prevIndex].
                                   get(baseType).ConnectedFragments.get(connectedRead).iterator();
                               HashSet testHash = new HashSet();
                               while(it.hasNext())
                               {
                                   int val = (int)it.next();
                                   if(val >= index)
                                        testHash.add(val + addedInsertions);
                                   else
                                       testHash.add(val);
                               }

                               fragments[prevIndex].
                                   get(baseType).ConnectedFragments.get(connectedRead).clear();
                               fragments[prevIndex].
                                   get(baseType).ConnectedFragments.get(connectedRead).addAll(testHash);
                       }
                    }                   
                }
                
                insertionCount+=addedInsertions;
                index = endIndex;
                //newRefIndex++;
            }else{
                
                for(String baseType:fragments[newRefIndex].keySet()){
                    for(String connectedRead:fragments[newRefIndex].
                        get(baseType).ConnectedFragments.keySet()){
                        
                            Iterator it=fragments[newRefIndex].
                                get(baseType).ConnectedFragments.get(connectedRead).iterator();
                            HashSet testHash = new HashSet();
                            while(it.hasNext())
                            {
                                int val = (int)it.next();
                                testHash.add(val + insertionCount);
                            }

                            fragments[newRefIndex].
                                get(baseType).ConnectedFragments.get(connectedRead).clear();
                            fragments[newRefIndex].
                                get(baseType).ConnectedFragments.get(connectedRead).addAll(testHash);
                    }
                }
                newRefIndex++;
            }
        }
        
        System.err.println("");
    }
      
    public boolean ReadArrayContainsInsert(Read targetRead,Read[] readArr){
        for(Read read:readArr){
            if(read!=null && targetRead.getID() == read.getID())
                return true;
        }
        
        return false;
    }
    
    public void AddInsertedBases(Map<String,FragmentNode>[] fragments, int index,
            String insertedBases,FragmentNode fragmentNode){
        for(int charIndex = 0;charIndex<insertedBases.length();charIndex++){
            
            if(fragments[index]==null)
                fragments[index] = new HashMap();
            
            FragmentNode tempNode = new FragmentNode();
            tempNode.ReadCount = fragmentNode.ReadCount;
            tempNode.ConnectedFragments = new HashMap();
            
            for(String base:fragmentNode.ConnectedFragments.keySet()){
                tempNode.ConnectedFragments.put(base, new HashSet());
                
                for(Object val:fragmentNode.ConnectedFragments.get(base)){
                    tempNode.ConnectedFragments.get(base).add(val);
                }
            }
            
            fragments[index].put(Character.toString(insertedBases.charAt(charIndex)), 
                    tempNode);
            index++;
        }
    }
    
    public int getMaxReadCount(){
        return maxReadCount;
    }
    
    public Map<String,FragmentNode>[] CombineFragments(ArrayList<String> referenceData,
            Map<String,FragmentNode>[] frag00,
            Map<String,FragmentNode>[] frag01,
            ArrayList<String> ref00,ArrayList<String> ref01,
            int size){
        Map<String,FragmentNode>[] combinedFragments = new HashMap[size];
        
        int index00 = 0;
        int index01 = 0;
        for(int indexMaster = 0;indexMaster<referenceData.size();indexMaster++){

            if(ref00.get(index00).
                equals(ref01.get(index01))){

                combinedFragments[indexMaster] = frag00[index00];
                    
                for(String comparedBase:frag01[index01].keySet()){
                    if(combinedFragments[indexMaster].containsKey(comparedBase)){
                        //Update the read count 
                        combinedFragments[indexMaster].get(comparedBase).ReadCount+=
                                frag01[index01].get(comparedBase).ReadCount;
                        //Update connected reads
                        for(String connectedRead:frag01[index01].
                                get(comparedBase).ConnectedFragments.keySet()){
                            //Both Fragments have this connected read
                            if(combinedFragments[indexMaster].get(comparedBase).
                                    ConnectedFragments.containsKey(connectedRead)){
                                //Merge connected columns
                                combinedFragments[indexMaster].get(comparedBase).ConnectedFragments.
                                        get(connectedRead).addAll(
                                             frag01[index01].
                                                    get(comparedBase).ConnectedFragments.get(connectedRead)
                                        );
                            }else{
                                combinedFragments[indexMaster].get(comparedBase).ConnectedFragments.
                                        put(connectedRead, frag01[index01].
                                                    get(comparedBase).ConnectedFragments.get(connectedRead));
                            }
                        }
                    }else{
                        combinedFragments[indexMaster].put(comparedBase, 
                                frag01[index01].get(comparedBase));
                    }
                }

                index00++;
                index01++;
            }else{
                if(ref00.get(index00).equals("INS")){
                    
                    if(combinedFragments[indexMaster] == null)
                        combinedFragments[indexMaster]= new HashMap();
                    
                    combinedFragments[indexMaster].putAll(frag00[index00]);                    
                    index00++;
                }
                else if(ref01.get(index01).equals("INS")){
                    
                    if(combinedFragments[indexMaster] == null)
                        combinedFragments[indexMaster]= new HashMap();
                    
                    combinedFragments[indexMaster].putAll(frag01[index01]); 
                  
                    index01++;
                }
            }     
        }        
        
        return combinedFragments;
    }
    
    public void AdjustForInsertions(ArrayList<String> primaryRef,
            Map<String,FragmentNode>[] fragments,ArrayList<String> combinedRef){

        int primaryIndex = 0;
        int offset = 0;
        int addedInserts=0;
        int offSetDiff = 0;
        for(int combinedIndex = 0;combinedIndex<combinedRef.size()-1;combinedIndex++){
            if(combinedRef.get(combinedIndex+1).equals("INS"))
            {               
                if(primaryRef.get(primaryIndex+1).equals("INS")){
                    if(((ProbeToNextFragment(combinedIndex, combinedRef) - combinedIndex - 1) - 
                            (ProbeToNextFragment(primaryIndex, primaryRef) - primaryIndex - 1)) >= 1 ){
                        offSetDiff = (ProbeToNextFragment(combinedIndex, combinedRef) - combinedIndex - 1) - 
                                            (ProbeToNextFragment(primaryIndex, primaryRef) - primaryIndex - 1);
                        combinedIndex = (ProbeToNextFragment(combinedIndex, combinedRef) - combinedIndex - 1)
                                + combinedIndex;
                        addedInserts+=offSetDiff;
                        
                        int forwardLookIndex = (ProbeToNextFragment(primaryIndex, primaryRef) - primaryIndex - 1);
                        for(int i = 0;i<forwardLookIndex;i++){
                            for(String baseType:fragments[primaryIndex+i+1].keySet()){
                                for(String connectedRead:fragments[primaryIndex+i+1].
                                    get(baseType).ConnectedFragments.keySet()){

                                        Iterator it=fragments[primaryIndex+i+1].
                                            get(baseType).ConnectedFragments.get(connectedRead).iterator();
                                        HashSet testHash = new HashSet();
                                        while(it.hasNext())
                                        {
                                            int val = (int)it.next();
                                            testHash.add(val+addedInserts);
                                        }

                                        fragments[primaryIndex+i+1].
                                            get(baseType).ConnectedFragments.get(connectedRead).clear();
                                        fragments[primaryIndex+i+1].
                                            get(baseType).ConnectedFragments.get(connectedRead).addAll(testHash);
                                }                                
                            }   
                        }
                    }
                }else{               
                    
                    if(primaryRef.get(primaryIndex).equals("INS")){
                        primaryIndex++;
                        addedInserts++;
                        continue;
                    }
                    
                    offset = ProbeToNextFragment(combinedIndex, combinedRef) - combinedIndex - 1;
                    combinedIndex = offset + combinedIndex;
                    addedInserts+=offset;
                }
            }

                for(String baseType:fragments[primaryIndex].keySet()){
                    for(String connectedRead:fragments[primaryIndex].
                        get(baseType).ConnectedFragments.keySet()){
                        
                            Iterator it=fragments[primaryIndex].
                                get(baseType).ConnectedFragments.get(connectedRead).iterator();
                            HashSet testHash = new HashSet();
                            while(it.hasNext())
                            {
                                int val = (int)it.next();
                                if(primaryRef.get(val).equals("INS"))
                                    testHash.add(val+(addedInserts-offSetDiff));
                                else
                                    testHash.add(val+addedInserts);
                            }

                            fragments[primaryIndex].
                                get(baseType).ConnectedFragments.get(connectedRead).clear();
                            fragments[primaryIndex].
                                get(baseType).ConnectedFragments.get(connectedRead).addAll(testHash);
                    }
                
                }
            primaryIndex++;

        }
    }
    

    
    public int ProbeToNextFragment(int currentIndex,ArrayList<String> reference){
        currentIndex++;
        while(reference.get(currentIndex)=="INS")
            currentIndex++;
        
        return currentIndex;
    }
       
    public void FragmentPrinter(Map<String,FragmentNode>[] fragments){
        System.out.println("Printing Fragments\n");
        
        for(int index = 0;index<fragments.length;index++){
            if(fragments[index]!=null){
                for(String baseVal:fragments[index].keySet()){
                    System.out.print(baseVal + "(" + fragments[index].get(baseVal).ReadCount + ") ");
                    
                    for(String connectedFrag:fragments[index].
                            get(baseVal).ConnectedFragments.keySet()){
                        System.out.print(connectedFrag);
                        for(Object connectedIndex:fragments[index].get(baseVal).ConnectedFragments.get(connectedFrag)){
                            System.out.print("["+connectedIndex+"]");
                        }
                    }
                    
                    System.out.println("");
                }
            }
            System.out.println("");
        }
    }
}