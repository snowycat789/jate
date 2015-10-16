package org.apache.lucene.analysis.jate;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import uk.ac.shef.dcs.jate.nlp.POSTagger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by - on 16/10/2015.
 */
public class OpenNLPPOSTaggerFilter extends TokenFilter {
    private POSTagger tagger;
    private int tokenIdx = 0;
    protected boolean first = true;
    // cloned attrs of all tokens
    protected List<AttributeSource> tokenAttrs = new ArrayList<>();

    private final PayloadAttribute exitingPayload = addAttribute(PayloadAttribute.class);

    private String[] posTags;
    /**
     * Construct a token stream filtering the given input.
     *
     * @param input
     */
    protected OpenNLPPOSTaggerFilter(TokenStream input, POSTagger tagger) {
        super(input);
        this.tagger=tagger;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        if (first) {
            //gather all tokens from doc
            String[] words = walkTokens();
            if (words.length == 0) {
                return false;
            }
            //tagging
            posTags = createTags(words);
            first = false;
            tokenIdx = 0;
        }

        if (tokenIdx == tokenAttrs.size()) {
            resetParams();
            return false;
        }

        String string = exitingPayload.getPayload().utf8ToString()+",";
        exitingPayload.setPayload(new BytesRef((string+posTags[tokenIdx]).getBytes("UTF-8")));
        tokenIdx++;
        return true;
    }

    protected String[] walkTokens() throws IOException {
        List<String> wordList = new ArrayList<>();
        while (input.incrementToken()) {
            CharTermAttribute textAtt = input.getAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAtt = input.getAttribute(OffsetAttribute.class);
            char[] buffer = textAtt.buffer();
            String word = new String(buffer, 0, offsetAtt.endOffset() - offsetAtt.startOffset());
            wordList.add(word);
            AttributeSource attrs = input.cloneAttributes();
            tokenAttrs.add(attrs);
        }
        String[] words = new String[wordList.size()];
        for (int i = 0; i < words.length; i++) {
            words[i] = wordList.get(i);
        }
        return words;
    }

    protected String[] createTags(String[] words) {
        //String[] appended = appendDot(words);
        return assignPOS(words);
    }


    protected String[] assignPOS(String[] words) {

        return tagger.tag(words);
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        clearAttributes();
        resetParams();
    }

    @Override
    public final void end() throws IOException {
        super.end();
        clearAttributes();
        tokenAttrs.clear();
    }

    protected void resetParams() {
        first = true;
        tokenIdx = 0;
        posTags=null;
    }
}
