/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.sectionadapter;

import com.cyngn.eleven.utils.SectionCreatorUtils;

import java.util.List;
import java.util.TreeMap;

/**
 * Simple Container that contains a list of T items as well as the map of section information
 * @param <T> the type of item that the list contains
 */
public class SectionListContainer<T> {
    public TreeMap<Integer, SectionCreatorUtils.Section> mSections;
    public List<T> mListResults;

    public SectionListContainer(final TreeMap<Integer, SectionCreatorUtils.Section> sections,
                                final List<T> results) {
        mSections = sections;
        mListResults = results;
    }
}
