package com.zimbra.qa.unittest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import com.zimbra.common.util.SetUtil;
import com.zimbra.cs.account.NamedEntry;

public class TestProvisioningUtil extends TestCase {
    
    private static String NAME_ROOT_DOMAIN     = "ldap-test-domain";
    
    public static String genTestId() {
        Date date = new Date();
        SimpleDateFormat fmt =  new SimpleDateFormat("yyyyMMdd-HHmmss");
        return fmt.format(date);
    }
    
    public static String baseDomainName(String testName, String testId) {
        return testName + "-" + testId + "." + NAME_ROOT_DOMAIN;
    }

    public static void verifySameId(NamedEntry entry1, NamedEntry entry2) throws Exception {
        assertNotNull(entry1);
        assertNotNull(entry2);
        assertEquals(entry1.getId(), entry2.getId());
    }
    
    
    public static void verifySameEntry(NamedEntry entry1, NamedEntry entry2) throws Exception {
        verifySameId(entry1, entry2);
        assertEquals(entry1.getName(), entry2.getName());
    }
    
    // verify list contains all the entries
    // if checkCount == true, verify the count matches too
    public static void verifyEntries(List<NamedEntry> list, NamedEntry[] entries, boolean checkCount) throws Exception {
        try {
            if (checkCount)
                assertEquals(list.size(), entries.length);
        
            Set<String> ids = new HashSet<String>();
            for (NamedEntry entry : list)
                ids.add(entry.getId());
            
            for (NamedEntry entry : entries) {
                assertTrue(ids.contains(entry.getId()));
                ids.remove(entry.getId());
            }
            
            // make sure all ids in list is present is entries
            if (checkCount)
                assertEquals(ids.size(), 0);
         
        } catch (AssertionFailedError e) {
            System.out.println("\n===== verifyEntries failed =====");
            System.out.println("Message:" + e.getMessage());
            
            System.out.println("\nlist contains " + list.size() + " entries:");
            for (NamedEntry entry : list)
                System.out.println("    " + entry.getName());
            System.out.println("entries contains " + entries.length + " entries:");
            for (NamedEntry entry : entries)
                System.out.println("    " + entry.getName());
            
            System.out.println();
            throw e;
        }
    }
    
    // verify list of NamedEntry contains all the ids
    // if checkCount == true, verify the count matches too
    public static void verifyEntriesById(List<NamedEntry> list, String[] names, boolean checkCount) throws Exception {
        Set<String> idsInList = new HashSet<String>();
        for (NamedEntry entry : list)
            idsInList.add(entry.getId());
        
        verifyEntries(idsInList, names, checkCount);
    }
    
    // verify list of NamedEntry contains all the names
    // if checkCount == true, verify the count matches too
    public static void verifyEntriesByName(List<NamedEntry> list, String[] names, boolean checkCount) throws Exception {
        Set<String> namesInList = new HashSet<String>();
        for (NamedEntry entry : list)
            namesInList.add(entry.getName());
        
        verifyEntries(namesInList, names, checkCount);
    }
    
    // verify list contains all the names
    // if checkCount == true, verify the count matches too
    public static void verifyEntries(Set<String> list, String[] names, boolean checkCount) throws Exception {
        try {
            if (checkCount)
                assertEquals(names.length, list.size());
            
            for (String name : names)
                assertTrue(list.contains(name));
         
        } catch (AssertionFailedError e) {
            System.out.println("\n===== verifyEntries failed =====");
            System.out.println("Message:" + e.getMessage());
            
            System.out.println("\nlist contains " + list.size() + " entries:");
            for (String name : list)
                System.out.println("    " + name);
            System.out.println("entries contains " + names.length + " entries:");
            for (String name : names)
                System.out.println("    " + name);
            
            System.out.println();
            throw e;
        }
    }
    
    public static void verifyEquals(Set<String> expected, Set<String> atual) throws Exception {
        assertEquals(0, SetUtil.subtract(expected, atual).size());
        assertEquals(0, SetUtil.subtract(atual, expected).size());
    }
}
