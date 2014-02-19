/** Copyright (c) 2012 Memorial Sloan-Kettering Cancer Center.
**
** This library is free software; you can redistribute it and/or modify it
** under the terms of the GNU Lesser General Public License as published
** by the Free Software Foundation; either version 2.1 of the License, or
** any later version.
**
** This library is distributed in the hope that it will be useful, but
** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
** documentation provided hereunder is on an "as is" basis, and
** Memorial Sloan-Kettering Cancer Center 
** has no obligations to provide maintenance, support,
** updates, enhancements or modifications.  In no event shall
** Memorial Sloan-Kettering Cancer Center
** be liable to any party for direct, indirect, special,
** incidental or consequential damages, including lost profits, arising
** out of the use of this software and its documentation, even if
** Memorial Sloan-Kettering Cancer Center 
** has been advised of the possibility of such damage.  See
** the GNU Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public License
** along with this library; if not, write to the Free Software Foundation,
** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
**/

package org.mskcc.cbio.portal.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.biojava.bio.structure.AminoAcid;
import org.biojava.bio.structure.Chain;
import org.biojava.bio.structure.Group;
import org.biojava.bio.structure.ResidueNumber;
import org.biojava.bio.structure.Structure;
import org.biojava.bio.structure.align.util.AtomCache;
import org.biojava.bio.structure.io.FileParsingParameters;
import org.biojava3.core.sequence.compound.AminoAcidCompound;
import org.biojava3.core.sequence.compound.AminoAcidCompoundSet;
import org.biojava3.core.sequence.loader.UniprotProxySequenceReader;
import org.mskcc.cbio.portal.dao.DaoPdbUniprotResidueMapping;
import org.mskcc.cbio.portal.dao.DaoException;
import org.mskcc.cbio.portal.dao.MySQLbulkLoader;
import org.mskcc.cbio.portal.model.PdbUniprotAlignment;
import org.mskcc.cbio.portal.model.PdbUniprotResidueMapping;
import org.mskcc.cbio.portal.util.ConsoleUtil;
import org.mskcc.cbio.portal.util.FileUtil;
import org.mskcc.cbio.portal.util.ProgressMonitor;

/**
 *
 * @author jgao
 */
public final class ImportPdbUniprotResidueMapping {
    private ImportPdbUniprotResidueMapping() {}

    /**
     * 
     *
     * @param mappingFile pdb-uniprot-residue-mapping.txt.
     * @param pMonitor Progress Monitor.
     */
    public static void importMutationAssessorData(File mappingFile, ProgressMonitor pMonitor) throws DaoException, IOException {
        MySQLbulkLoader.bulkLoadOn();
        FileReader reader = new FileReader(mappingFile);
        BufferedReader buf = new BufferedReader(reader);
        String line = buf.readLine();
        int alignId = 0;
        PdbUniprotAlignment pdbUniprotAlignment = new PdbUniprotAlignment();
        List<PdbUniprotResidueMapping> pdbUniprotResidueMappings = Collections.emptyList();
        Map<Integer, Integer> mappingUniPdbProtein = Collections.emptyMap();
        Map<Integer, Integer> mappingUniPdbAlignment = Collections.emptyMap();
        Map<Integer, Integer> mappingPdbUniProtein = Collections.emptyMap();
        Map<Integer, Integer> mappingPdbUniAlignment = Collections.emptyMap();
        
        while (line != null) {
            if (!line.startsWith("#")) {
                String parts[] = line.split("\t",-1);
                if (line.startsWith(">")) {
                    // alignment line, eg. >1a37   A       1433B_HUMAN     1       32      3       34      0.000000        29.000000       90.625000       MDKSELVQKAKLAEQAERYDDMAAAMKAVTEQ        MDKNELVQKAKLAEQAERYDDMAACMKSVTEQ        MDK+ELVQKAKLAEQAERYDDMAA MK+VTEQ
                    
                    if (!pdbUniprotResidueMappings.isEmpty()) {
                        DaoPdbUniprotResidueMapping.addPdbUniprotAlignment(pdbUniprotAlignment);
                        for (PdbUniprotResidueMapping mapping : pdbUniprotResidueMappings) {
                            DaoPdbUniprotResidueMapping.addPdbUniprotResidueMapping(mapping);
                        }
                        mappingUniPdbProtein.putAll(mappingUniPdbAlignment);
                        mappingPdbUniProtein.putAll(mappingPdbUniAlignment);
                    }
                    
                    String pdbId = parts[0].substring(1);
                    if (!pdbId.equals(pdbUniprotAlignment.getPdbId())
                            || !parts[1].equals(pdbUniprotAlignment.getChain())
                            || !parts[2].equals(pdbUniprotAlignment.getUniprotId())) {
                        mappingUniPdbProtein = new HashMap<Integer, Integer>();
                        mappingPdbUniProtein = new HashMap<Integer, Integer>();
                    }
                    
                    pdbUniprotAlignment.setAlignmentId(++alignId);
                    
                    pdbUniprotAlignment.setPdbId(pdbId);
                    pdbUniprotAlignment.setChain(parts[1]);
                    pdbUniprotAlignment.setUniprotId(parts[2]);
                    
                    pdbUniprotAlignment.setPdbFrom(Integer.parseInt(parts[3]));
                    pdbUniprotAlignment.setPdbTo(Integer.parseInt(parts[4]));
                    pdbUniprotAlignment.setUniprotFrom(Integer.parseInt(parts[5]));
                    pdbUniprotAlignment.setUniprotTo(Integer.parseInt(parts[6]));
                    pdbUniprotAlignment.setEValue(Float.parseFloat(parts[7]));
                    pdbUniprotAlignment.setIdentity(Float.parseFloat(parts[8]));
                    pdbUniprotAlignment.setIdentityPerc(Float.parseFloat(parts[9]));
                    pdbUniprotAlignment.setUniprotAlign(parts[10]);
                    pdbUniprotAlignment.setPdbAlign(parts[11]);
                    pdbUniprotAlignment.setMidlineAlign(parts[12]);
                    
                    pdbUniprotResidueMappings = new ArrayList<PdbUniprotResidueMapping>();
                    mappingUniPdbAlignment = new HashMap<Integer, Integer>();
                    mappingPdbUniAlignment = new HashMap<Integer, Integer>();
                    
                } else {
                    // residue mapping line, e.g. 1a37    A       M1      1433B_HUMAN     M3      M
                    int pdbPos = Integer.parseInt(parts[2].substring(1));
                    int uniprotPos = Integer.parseInt(parts[4].substring(1));
                    Integer prePdb = mappingUniPdbProtein.get(uniprotPos);
                    Integer preUni = mappingPdbUniProtein.get(pdbPos);
                    if ((prePdb!=null && prePdb!=pdbPos) || (preUni!=null && preUni!=uniprotPos)) {
                        // mismatch
                        pdbUniprotResidueMappings.clear();
                        while (line !=null && !line.startsWith(">")) {
                            line = buf.readLine();
                            pMonitor.incrementCurValue();
                            ConsoleUtil.showProgress(pMonitor);
                        }
                        continue;
                    }
                    
                    mappingUniPdbAlignment.put(uniprotPos, pdbPos);
                    mappingPdbUniAlignment.put(pdbPos, uniprotPos);
                    
                    String match = parts[5].length()==0 ? " " : parts[5];
                    PdbUniprotResidueMapping pdbUniprotResidueMapping = new PdbUniprotResidueMapping(alignId, pdbPos, null, pdbPos, uniprotPos, match);
                    pdbUniprotResidueMappings.add(pdbUniprotResidueMapping);
                }

            }
            
            line = buf.readLine();
            
            pMonitor.incrementCurValue();
            ConsoleUtil.showProgress(pMonitor);
        }
        
        // last one
        if (!pdbUniprotResidueMappings.isEmpty()) {
            DaoPdbUniprotResidueMapping.addPdbUniprotAlignment(pdbUniprotAlignment);
            for (PdbUniprotResidueMapping mapping : pdbUniprotResidueMappings) {
                DaoPdbUniprotResidueMapping.addPdbUniprotResidueMapping(mapping);
            }
        }

        //  Flush database
        if (MySQLbulkLoader.isBulkLoad()) {
           MySQLbulkLoader.flushAll();
        }
    }

    /**
     * 
     *
     * @param mappingFile pdb-uniprot-residue-mapping.txt.
     * @param pMonitor Progress Monitor.
     */
    public static void importSiftsData(File mappingFile, Set<String> humanChains,
            String pdbCacheDir, double identp_threhold, ProgressMonitor pMonitor)
            throws DaoException, IOException {
        MySQLbulkLoader.bulkLoadOn();
        FileReader reader = new FileReader(mappingFile);
        BufferedReader buf = new BufferedReader(reader);
        int alignId = 0;
        
        String line = buf.readLine();
        while (line.startsWith("#")) {
            line = buf.readLine();
        }
        
            
        AtomCache atomCache = getAtomCache(pdbCacheDir);
            
        buf.readLine(); // skip head
        
        for (; line != null; line = buf.readLine()) {
            
            pMonitor.incrementCurValue();
            ConsoleUtil.showProgress(pMonitor);
            
            String[] parts = line.split("\t");
            String pdbId = parts[0];
            String chainId = parts[1];
            
            if (!humanChains.contains(pdbId+"."+chainId)) {
                continue;
            }
            
            System.out.println("processing "+line);
            
            String uniprotId = parts[2];
            
            int pdbSeqResBeg = Integer.parseInt(parts[3]);
            int pdbSeqResEnd = Integer.parseInt(parts[4]);
            int uniprotResBeg = Integer.parseInt(parts[7]);
            int uniprotResEnd = Integer.parseInt(parts[8]);
            
            if (pdbSeqResBeg-pdbSeqResEnd != uniprotResBeg-uniprotResEnd) {
                System.err.println("*** Lengths not equal");
                continue;
            }
            
//            String pdbAtomResBeg = parts[5]; // could have insertion code
//            String pdbAtomResEnd = parts[6]; // could have insertion code
            
            
            PdbUniprotAlignment pdbUniprotAlignment = new PdbUniprotAlignment();
            List<PdbUniprotResidueMapping> pdbUniprotResidueMappings = new ArrayList<PdbUniprotResidueMapping>();
            
            if (processPdbUniprotAlignment(pdbUniprotAlignment, pdbUniprotResidueMappings,
                    ++alignId, pdbId, chainId, uniprotId, uniprotResBeg,
                    uniprotResEnd, pdbSeqResBeg, pdbSeqResEnd, identp_threhold, atomCache)) {
                DaoPdbUniprotResidueMapping.addPdbUniprotAlignment(pdbUniprotAlignment);
                for (PdbUniprotResidueMapping mapping : pdbUniprotResidueMappings) {
                    DaoPdbUniprotResidueMapping.addPdbUniprotResidueMapping(mapping);
                }
            }
        }

        //  Flush database
        if (MySQLbulkLoader.isBulkLoad()) {
           MySQLbulkLoader.flushAll();
        }
    }
    
    private static boolean processPdbUniprotAlignment(
            PdbUniprotAlignment pdbUniprotAlignment, List<PdbUniprotResidueMapping> pdbUniprotResidueMappings,
            int alignId, String pdbId, String chainId, String uniprotId, int uniprotResBeg,
            int uniprotResEnd, int pdbSeqResBeg, int pdbSeqResEnd, double identp_threhold, AtomCache atomCache) {

        String uniprotSeq = getUniprotSequence(uniprotId, uniprotResBeg, uniprotResEnd);
        if (uniprotSeq==null) {
            System.err.println("Could not read UniProt Sequence");
            return false;
        }
        
        List<Group>pdbResidues = getPdbResidues(atomCache, pdbId, chainId, pdbSeqResBeg, pdbSeqResEnd);
        
        int len = uniprotResEnd-uniprotResBeg+1;
        
        if (pdbResidues.size()!=len) {
            System.err.println("*** Lengths not correct from structure");
            return false;
        }
        
        int start = 0;
        for (; start<len; start++) {
            if (pdbResidues.get(start).getResidueNumber()!=null) {
                break;
            }
        }
        
        if (start==len) {
            System.err.print("No atom residues");
            return false;
        }
        
        int end;
        for (end=len; end>start; end--) {
            if (pdbResidues.get(end-1).getResidueNumber()!=null) {
                break;
            }
        }
        
        int identity = 0;
        StringBuilder midline = new StringBuilder();
        StringBuilder pdbAlign = new StringBuilder();
        for (int i=start; i<end; i++) {
            Group pdbResidue = pdbResidues.get(i);
            if (!(pdbResidue instanceof AminoAcid)) {
                System.err.println("*** Non amino acid");
                return false;
            }
            
            ResidueNumber rn = pdbResidue.getResidueNumber();
            
            char pdbAA = ((AminoAcid)pdbResidue).getAminoType();
            char uniprotAA = uniprotSeq.charAt(i);
            char match = ' ';
            
            if (rn==null) { // if not a atom residue
                match = '-';
            } else if (pdbAA == uniprotAA) {
                identity++;
                match = pdbAA;
            }
            
            midline.append(match);
            pdbAlign.append(pdbAA);
            
            if (rn!=null) {
                PdbUniprotResidueMapping pdbUniprotResidueMapping = 
                        new PdbUniprotResidueMapping(alignId, rn.getSeqNum(),
                        rn.getInsCode()==null?null:rn.getInsCode().toString(),
                        pdbSeqResBeg+i, uniprotResBeg+i, ""+match);
                pdbUniprotResidueMappings.add(pdbUniprotResidueMapping);
            }
        }
        
        double identp = identity*100.0/(end-start);
        
        if (identp < identp_threhold) {
            System.out.print("*** low identp: "+identp);
            return false;
        }
        
        pdbUniprotAlignment.setAlignmentId(alignId);

        pdbUniprotAlignment.setPdbId(pdbId);
        pdbUniprotAlignment.setChain(chainId);
        pdbUniprotAlignment.setUniprotId(uniprotId);
        
        pdbUniprotAlignment.setPdbFrom(pdbSeqResBeg+start);
        pdbUniprotAlignment.setPdbTo(pdbSeqResBeg+end-1);
        pdbUniprotAlignment.setUniprotFrom(uniprotResBeg+start);
        pdbUniprotAlignment.setUniprotTo(uniprotResBeg+end-1);
//        pdbUniprotAlignment.setEValue(null);
        pdbUniprotAlignment.setUniprotAlign(uniprotSeq.substring(start, end));
        
        pdbUniprotAlignment.setIdentity((float)identity);
        pdbUniprotAlignment.setIdentityPerc((float)(identp));
        pdbUniprotAlignment.setPdbAlign(pdbAlign.toString());
        pdbUniprotAlignment.setMidlineAlign(midline.toString());
        
        return true;
}
    
    private static AtomCache getAtomCache(String dirCache) {
        AtomCache atomCache = new AtomCache(dirCache, true);
        FileParsingParameters params = new FileParsingParameters();
        params.setLoadChemCompInfo(false);
        params.setAlignSeqRes(true);
        params.setParseSecStruc(false);
        params.setUpdateRemediatedFiles(false);
        atomCache.setFileParsingParams(params);
        atomCache.setAutoFetch(true);
        return atomCache;
    }
    
    private static List<Group> getPdbResidues(AtomCache atomCache, String pdbId, String chainId, int start, int end) {
        try {
            Structure struc = atomCache.getStructure(pdbId);
            
            if (struc!=null) {
                Chain chain = struc.getChainByPDB(chainId);
                return chain.getSeqResGroups().subList(start-1, end);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
            return Collections.emptyList();
    }
    
    private static String getUniprotSequence(String uniportAcc, int start, int end) {
        try {
            UniprotProxySequenceReader<AminoAcidCompound> uniprotSequence
                    = new UniprotProxySequenceReader<AminoAcidCompound>(uniportAcc, AminoAcidCompoundSet.getAminoAcidCompoundSet());
            return uniprotSequence.getSequenceAsString().substring(start-1, end);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static Set<String> readHumanChains(String file) throws IOException {
        Set<String> humanChains = new HashSet<String>();
        FileReader reader = new FileReader(file);
        BufferedReader buf = new BufferedReader(reader);
        for (String line = buf.readLine(); line != null; line = buf.readLine()) {
            String[] parts = line.split("\t");
            humanChains.add(parts[0]+"."+parts[1]);
        }
        return humanChains;
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("command line usage:  importPdbUniprotResidueMapping.pl <pdb_chain_uniprot.tsv> <pdb_chain_human.tsv> <pdb-cache-dir>");
            return;
        }
        
        String pdbCacheDir = args.length>2 ? args[2] : System.getProperty("java.io.tmpdir");
    
        ProgressMonitor pMonitor = new ProgressMonitor();
        pMonitor.setConsoleMode(true);
        
        double identp_threhold = 80;

        try {
            DaoPdbUniprotResidueMapping.deleteAllRecords();
            
            Set<String> humanChains = readHumanChains(args[1]);
            
            File file = new File(args[0]);
            System.out.println("Reading PDB-UniProt residue mapping from:  " + file.getAbsolutePath());
            int numLines = FileUtil.getNumLines(file);
            System.out.println(" --> total number of lines:  " + numLines);
            pMonitor.setMaxValue(numLines);
            importSiftsData(file, humanChains, pdbCacheDir, identp_threhold, pMonitor);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (DaoException e) {
            e.printStackTrace();
        } finally {
            ConsoleUtil.showWarnings(pMonitor);
            System.err.println("Done.");
        }
    }
}
