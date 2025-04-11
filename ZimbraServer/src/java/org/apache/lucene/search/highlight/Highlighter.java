package org.apache.lucene.search.highlight;
/**
 * Copyright 2002-2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.PriorityQueue;

/**
 * Class used to markup highlighted terms found in the best sections of a 
 * text, using configurable {@link Fragmenter}, {@link Scorer}, {@link Formatter} 
 * and tokenizers.
 * @author mark@searcharea.co.uk
 */
public class Highlighter
{

	public static final  int DEFAULT_MAX_DOC_BYTES_TO_ANALYZE=50*1024;
	private int maxDocBytesToAnalyze=DEFAULT_MAX_DOC_BYTES_TO_ANALYZE;
	private Formatter formatter;
	private Fragmenter textFragmenter=new SimpleFragmenter();
	private Scorer fragmentScorer=null;

	public Highlighter(Scorer fragmentScorer)
	{
		this(new SimpleHTMLFormatter(),fragmentScorer);
	}
	
	
	public Highlighter(Formatter formatter, Scorer fragmentScorer)
	{
		this.formatter = formatter;
		this.fragmentScorer = fragmentScorer;
	}
	



	/**
	 * Highlights chosen terms in a text, extracting the most relevant section.
	 * The document text is analysed in chunks to record hit statistics
	 * across the document. After accumulating stats, the fragment with the highest score
	 * is returned
	 *
	 * @param tokenStream   a stream of tokens identified in the text parameter, including offset information. 
	 * This is typically produced by an analyzer re-parsing a document's 
	 * text. Some work may be done on retrieving TokenStreams more efficently 
	 * by adding support for storing original text position data in the Lucene
	 * index but this support is not currently available (as of Lucene 1.4 rc2).  
	 * @param text text to highlight terms in
	 *
	 * @return highlighted text fragment or null if no terms found
	 */
	public final String getBestFragment(TokenStream tokenStream, String text)
		throws IOException
	{
		String[] results = getBestFragments(tokenStream,text, 1);
		if (results.length > 0)
		{
			return results[0];
		}
		return null;
	}
	/**
	 * Highlights chosen terms in a text, extracting the most relevant sections.
	 * The document text is analysed in chunks to record hit statistics
	 * across the document. After accumulating stats, the fragments with the highest scores
	 * are returned as an array of strings in order of score (contiguous fragments are merged into 
	 * one in their original order to improve readability)
	 *
	 * @param text        	text to highlight terms in
	 * @param maxNumFragments  the maximum number of fragments.
	 *
	 * @return highlighted text fragments (between 0 and maxNumFragments number of fragments)
	 */
	public final String[] getBestFragments(
		TokenStream tokenStream,	
		String text,
		int maxNumFragments)
		throws IOException
	{
		maxNumFragments = Math.max(1, maxNumFragments); //sanity check
		StringBuffer newText = new StringBuffer();
		
		TextFragment[] frag =getBestDocFragments(tokenStream,text, newText, maxNumFragments);

		mergeContiguousFragments(frag);

		//Get text
		ArrayList fragTexts = new ArrayList();
		int n = 0;
		for (int i = 0; i < frag.length; i++)
		{
			if ((frag[i] != null) && (frag[i].getScore() > 0))
			{
				fragTexts.add(
					newText.substring(
						frag[i].textStartPos,
						frag[i].textEndPos));
			}
		}
		return (String[]) fragTexts.toArray(new String[0]);
	}

	/**
	 * Low level api to get the most relevant sections of the document
	 * @param tokenStream
	 * @param text
	 * @param maxNumFragments
	 * @return 
	 * @throws IOException
	 */
	private final TextFragment[] getBestDocFragments(
		TokenStream tokenStream,	
		String text,
		StringBuffer newText,
		int maxNumFragments)
		throws IOException
	{
		ArrayList docFrags = new ArrayList();

		TextFragment currentFrag =	new TextFragment(newText.length(), docFrags.size());
		fragmentScorer.startFragment(currentFrag);
		docFrags.add(currentFrag);
	
		FragmentQueue fragQueue = new FragmentQueue(maxNumFragments);

		try
		{
			org.apache.lucene.analysis.Token token;
			String tokenText;
			int startOffset;
			int endOffset;
			int lastEndOffset = 0;
			textFragmenter.start(text);

			while ((token = tokenStream.next()) != null)
			{
				
				startOffset = token.startOffset();
				endOffset = token.endOffset();		
				//FIXME an issue was reported with CJKTokenizer that I couldnt reproduce
				// where the analyzer was producing overlapping tokens.
				// I suspect the fix is to make startOffset=Math.max(startOffset,lastEndOffset+1)
				// but cant be sure so I'll just leave this comment in for now
				tokenText = text.substring(startOffset, endOffset);


				// append text between end of last token (or beginning of text) and start of current token
				if (startOffset > lastEndOffset)
					newText.append(text.substring(lastEndOffset, startOffset));

				// does query contain current token?
				float score=fragmentScorer.getTokenScore(token);			
				newText.append(formatter.highlightTerm(tokenText, token.termText(), score, startOffset));
				

				if(textFragmenter.isNewFragment(token))
				{
					currentFrag.setScore(fragmentScorer.getFragmentScore());
					//record stats for a new fragment
					currentFrag.textEndPos = newText.length();
					currentFrag =new TextFragment(newText.length(), docFrags.size());
					fragmentScorer.startFragment(currentFrag);
					docFrags.add(currentFrag);
				}

				lastEndOffset = endOffset;
				if(lastEndOffset>maxDocBytesToAnalyze)
				{
					break;
				}
			}
			currentFrag.setScore(fragmentScorer.getFragmentScore());
			

			// append text after end of last token
			if (lastEndOffset < text.length())
				newText.append(text.substring(lastEndOffset));

			currentFrag.textEndPos = newText.length();

			//sort the most relevant sections of the text
			int minScore = 0;
			for (Iterator i = docFrags.iterator(); i.hasNext();)
			{
				currentFrag = (TextFragment) i.next();

				//If you are running with a version of Lucene before 11th Sept 03
				// you do not have PriorityQueue.insert() - so uncomment the code below					
				/*
									if (currentFrag.getScore() >= minScore)
									{
										fragQueue.put(currentFrag);
										if (fragQueue.size() > maxNumFragments)
										{ // if hit queue overfull
											fragQueue.pop(); // remove lowest in hit queue
											minScore = ((TextFragment) fragQueue.top()).getScore(); // reset minScore
										}
										
					
									}
				*/
				//The above code caused a problem as a result of Christoph Goller's 11th Sept 03
				//fix to PriorityQueue. The correct method to use here is the new "insert" method
				// USE ABOVE CODE IF THIS DOES NOT COMPILE!
				fragQueue.insert(currentFrag);
			}

			//return the most relevant fragments
			TextFragment frag[] = new TextFragment[fragQueue.size()];
			for (int i = frag.length - 1; i >= 0; i--)
			{
				frag[i] = (TextFragment) fragQueue.pop();
			}
			return frag;

		}
		finally
		{
			if (tokenStream != null)
			{
				try
				{
					tokenStream.close();
				}
				catch (Exception e)
				{
				}
			}
		}
	}


	/** Improves readability of a score-sorted list of TextFragments by merging any fragments 
	 * that were contiguous in the original text into one larger fragment with the correct order.
	 * This will leave a "null" in the array entry for the lesser scored fragment. 
	 * 
	 * @param frag An array of document fragments in descending score
	 */
	private void mergeContiguousFragments(TextFragment[] frag)
	{
		boolean mergingStillBeingDone;
		if (frag.length > 1)
			do
			{
				mergingStillBeingDone = false; //initialise loop control flag
				//for each fragment, scan other frags looking for contiguous blocks
				for (int i = 0; i < frag.length; i++)
				{
					if (frag[i] == null)
					{
						continue;
					}
					//merge any contiguous blocks 
					for (int x = 0; x < frag.length; x++)
					{
						if (frag[x] == null)
						{
							continue;
						}
						if (frag[i] == null)
						{
							break;
						}
						TextFragment frag1 = null;
						TextFragment frag2 = null;
						int frag1Num = 0;
						int frag2Num = 0;
						int bestScoringFragNum;
						int worstScoringFragNum;
						//if blocks are contiguous....
						if (frag[i].follows(frag[x]))
						{
							frag1 = frag[x];
							frag1Num = x;
							frag2 = frag[i];
							frag2Num = i;
						}
						else
							if (frag[x].follows(frag[i]))
							{
								frag1 = frag[i];
								frag1Num = i;
								frag2 = frag[x];
								frag2Num = x;
							}
						//merging required..
						if (frag1 != null)
						{
							if (frag1.getScore() > frag2.getScore())
							{
								bestScoringFragNum = frag1Num;
								worstScoringFragNum = frag2Num;
							}
							else
							{
								bestScoringFragNum = frag2Num;
								worstScoringFragNum = frag1Num;
							}
							frag1.merge(frag2);
							frag[worstScoringFragNum] = null;
							mergingStillBeingDone = true;
							frag[bestScoringFragNum] = frag1;
						}
					}
				}
			}
			while (mergingStillBeingDone);
	}
	
	
	/**
	 * Highlights terms in the  text , extracting the most relevant sections
	 * and concatenating the chosen fragments with a separator (typically "...").
	 * The document text is analysed in chunks to record hit statistics
	 * across the document. After accumulating stats, the fragments with the highest scores
	 * are returned in order as "separator" delimited strings.
	 *
	 * @param text        text to highlight terms in
	 * @param maxNumFragments  the maximum number of fragments.
	 * @param separator  the separator used to intersperse the document fragments (typically "...")
	 *
	 * @return highlighted text
	 */
	public final String getBestFragments(
		TokenStream tokenStream,	
		String text,
		int maxNumFragments,
		String separator)
		throws IOException
	{
		String sections[] =	getBestFragments(tokenStream,text, maxNumFragments);
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < sections.length; i++)
		{
			if (i > 0)
			{
				result.append(separator);
			}
			result.append(sections[i]);
		}
		return result.toString();
	}

	/**
	 * @return the maximum number of bytes to be tokenized per doc 
	 */
	public int getMaxDocBytesToAnalyze()
	{
		return maxDocBytesToAnalyze;
	}

	/**
	 * @param byteCount the maximum number of bytes to be tokenized per doc
	 * (This can improve performance with large documents)
	 */
	public void setMaxDocBytesToAnalyze(int byteCount)
	{
		maxDocBytesToAnalyze = byteCount;
	}

	/**
	 * @return
	 */
	public Fragmenter getTextFragmenter()
	{
		return textFragmenter;
	}

	/**
	 * @param fragmenter
	 */
	public void setTextFragmenter(Fragmenter fragmenter)
	{
		textFragmenter = fragmenter;
	}

	/**
	 * @return Object used to score each text fragment 
	 */
	public Scorer getFragmentScorer()
	{
		return fragmentScorer;
	}


	/**
	 * @param scorer
	 */
	public void setFragmentScorer(Scorer scorer)
	{
		fragmentScorer = scorer;
	}


}
class FragmentQueue extends PriorityQueue
{
	public FragmentQueue(int size)
	{
		initialize(size);
	}

	public final boolean lessThan(Object a, Object b)
	{
		TextFragment fragA = (TextFragment) a;
		TextFragment fragB = (TextFragment) b;
		if (fragA.getScore() == fragB.getScore())
			return fragA.fragNum > fragB.fragNum;
		else
			return fragA.getScore() < fragB.getScore();
	}
}
