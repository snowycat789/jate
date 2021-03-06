package uk.ac.shef.dcs.jate.feature;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.lucene.index.*;
import org.apache.solr.search.SolrIndexSearcher;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.util.SolrUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.apache.log4j.Logger;

/**
 *
 */
public class FrequencyCtxDocBasedFBMaster extends AbstractFeatureBuilder {
    private static final Logger LOG = Logger.getLogger(FrequencyCtxDocBasedFBMaster.class.getName());

    private int termOrWord; //0 means term; 1 means word

    public FrequencyCtxDocBasedFBMaster(SolrIndexSearcher solrIndexSearcher, JATEProperties properties,
                                        int termOrWord) {
        super(solrIndexSearcher, properties);
        this.termOrWord = termOrWord;
    }

    @Override
    public AbstractFeature build() throws JATEException {
        FrequencyCtxBased feature = new FrequencyCtxBased();
        try {
            Terms info = SolrUtil.getTermVector(properties.getSolrFieldNameJATENGramInfo(), solrIndexSearcher);
            Set<String> allLuceneTerms;
            if (termOrWord == 0)
                allLuceneTerms = getUniqueTerms();
            else
                allLuceneTerms = getUniqueWords();

            //start workers

            int cores = properties.getMaxCPUCores();
            cores = cores == 0 ? 1 : cores;
            int maxPerThread = allLuceneTerms.size() / cores;
            maxPerThread = getMaxPerThread(maxPerThread);

            LOG.info("Beginning building features. Total terms=" + allLuceneTerms.size() + ", cpu cores=" +
                    cores + ", max per core=" + maxPerThread);
            FrequencyCtxDocBasedFBWorker worker = new
                    FrequencyCtxDocBasedFBWorker(feature, properties, new ArrayList<>(allLuceneTerms),
                    solrIndexSearcher, maxPerThread,
                    info);
            ForkJoinPool forkJoinPool = new ForkJoinPool(cores);
            int total = forkJoinPool.invoke(worker);
            StringBuilder sb = new StringBuilder("Complete building features. Total processed terms = " + total);
            sb.append("/").append(allLuceneTerms.size());
            LOG.info(sb.toString());

        } catch (IOException ioe) {
            StringBuilder sb = new StringBuilder("Failed to build features!");
            sb.append("\n").append(ExceptionUtils.getFullStackTrace(ioe));
            LOG.error(sb.toString());
        }
        return feature;
    }

    private int getMaxPerThread(int maxPerThread) {
        if(maxPerThread < MIN_SEQUENTIAL_THRESHOLD) {
            maxPerThread = MIN_SEQUENTIAL_THRESHOLD;
        } else if (maxPerThread > MAX_SEQUENTIAL_THRESHOLD) {
            maxPerThread = MAX_SEQUENTIAL_THRESHOLD;
        }
        return maxPerThread;
    }
}
