/*
 * Copyright (C) 2006, The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.zimbra.kabuki.tools.img;

import javax.imageio.*;
import javax.imageio.stream.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

/*
 * DecodedFullColorImage represents a single PNG/JPG image that will be combined 
 * later.  It knows the original image's height, width, source filename, and 
 * target coordinates in the combined image.
 */
public class DecodedFullColorImage extends DecodedImage {

    private BufferedImage mBufImg;
    private String mSuffix;

    public DecodedFullColorImage(String filename,
                                 String suffix,
                                 String prefix,
                                 int layoutStyle
                                ) 
    {
        mFilename = filename;
        mSuffix = suffix;
        mPrefix = prefix;
        mLayoutStyle = layoutStyle;
    }

    public String getSuffix() { return mSuffix; }
    public BufferedImage getBufferedImage() { return mBufImg; }

    public int getWidth() { return mBufImg.getWidth(); }
    public int getHeight() { return mBufImg.getHeight(); }

    /*
     * Get a JavaScript definition for this piece of the combined image.
     * expects combinedFilename to be of the form "megaimage.gif".
     */
    public String getCssString(int combinedWidth,
                               int combinedHeight,
                               String combinedFilename,
                               boolean includeDisableCss) 
    {
        String css = super.getCssString(combinedWidth, combinedHeight, combinedFilename, includeDisableCss);
        // NOTE: This is an IE hack to make it use the AlphaImageLoader filter
        //		 instead of background-image for PNG files.
        if (mSuffix.equalsIgnoreCase("png")) {
            int head = css.indexOf("background-image:");
            int tail = css.indexOf(';', head);

            String selector = css.substring(0, css.indexOf('{'));
            String bgimage = css.substring(head, tail + 1);

            String cssAll = css.substring(0, head) +
                            "filter:progid:DXImageTransform.Microsoft.AlphaImageLoader(src='" + mPrefix +  combinedFilename + "',sizingMethod='scale');" +
                            css.substring(tail + 1);

            String cssIE = "\nHTML>BODY " + selector + "{" + bgimage + "}";

            css = cssAll + cssIE;
        }
        return css;
    }


    /*
     * Load the contents of this image
     */
    public void load() 
        throws java.io.IOException
    {
        Iterator iter = ImageIO.getImageReadersBySuffix(mSuffix);
        ImageReader reader = (ImageReader) iter.next();
        // make the input file be the input source for the ImageReader (decoder)
        reader.setInput(new FileImageInputStream(new File(mInputDir, mFilename)));
        mBufImg = reader.read(0);
    }

}