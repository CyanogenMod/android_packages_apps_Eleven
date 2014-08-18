/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.sectionadapter;

import java.util.List;
import java.util.TreeMap;

/**
 * Simple Container that contains a list of T items as well as the map of section information
 * @param <T> the type of item that the list contains
 */
public class SectionListContainer<T> {
    public TreeMap<Integer, String> mSectionIndices;
    public List<T> mListResults;

    public SectionListContainer(TreeMap<Integer, String> sectionIndices, List<T> results) {
        mSectionIndices = sectionIndices;
        mListResults = results;
    }
}
