package uk.ac.shef.dcs.jate.app;

import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.algorithm.ComboBasic;
import uk.ac.shef.dcs.jate.feature.*;
import uk.ac.shef.dcs.jate.model.JATETerm;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * see Nikita Astrakhantsev. 2015. Methods and software for terminology extraction from domainspecific text collection. In Ph.D. thesis. Institute for System
 Programming of Russian Academy of Sciences.
 */
public class AppComboBasic extends App {
    private static final Logger LOG = LoggerFactory.getLogger(AppComboBasic.class);

    /**
     * @param args
     *            command-line params accepting solr home path, solr core name
     *            and more optional run-time parameters
     * @see uk.ac.shef.dcs.jate.app.AppParams
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printHelp();
            System.exit(1);
        }

        String solrHomePath = args[args.length - 2];
        String solrCoreName = args[args.length - 1];

        Map<String, String> params = getParams(args);
        String jatePropertyFile = getJATEProperties(params);
        String corpusDir = getCorpusDir(params);

        List<JATETerm> terms;
        try {
            App app = new AppComboBasic(params);
            if (isCorpusProvided(corpusDir)) {
                app.index(Paths.get(corpusDir), Paths.get(solrHomePath), solrCoreName, jatePropertyFile);
            }

            terms = app.extract(solrHomePath, solrCoreName, jatePropertyFile);

            if (isExport(params)) {
                app.write(terms);
            }

            System.exit(0);
        } catch (IOException | JATEException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param initParams
     *            initial parameters including pre-filtering and post-filtering
     *            parameters
     * @throws JATEException
     * @see uk.ac.shef.dcs.jate.app.AppParams
     */
    public AppComboBasic(Map<String, String> initParams) throws JATEException {
        super(initParams);
    }

    @Override
    public List<JATETerm> extract(SolrCore core, String jatePropertyFile) throws IOException, JATEException {
        LOG.info("Start Basic term ranking and filtering for whole index ...");
        JATEProperties properties;

        properties = getJateProperties(jatePropertyFile);

        return extract(core, properties);
    }

    public List<JATETerm> extract(SolrCore core, JATEProperties properties) throws JATEException {
        SolrIndexSearcher searcher = core.getSearcher().get();
        try {

            this.freqFeatureBuilder = new FrequencyTermBasedFBMaster(searcher, properties, 0);
            this.freqFeature = (FrequencyTermBased) freqFeatureBuilder.build();

            Set<String> uniqueCandidateTerms = freqFeature.getMapTerm2TTF().keySet();
            TermComponentIndexFBMaster termCompIndexFeatureBuilder = new TermComponentIndexFBMaster(properties,
                    new ArrayList<>(uniqueCandidateTerms));
            TermComponentIndex termComponentIndexFeature = (TermComponentIndex) termCompIndexFeatureBuilder.build();

            ContainmentFBMaster cb = new ContainmentFBMaster(searcher, properties, termComponentIndexFeature,
                    uniqueCandidateTerms);
            Containment cf = (Containment) cb.build();

            Containment crf = (Containment)new ContainmentReverseBuilder(searcher, properties, cf).build();

            ComboBasic cbasic = new ComboBasic();
            cbasic.registerFeature(FrequencyTermBased.class.getName(), this.freqFeature);
            cbasic.registerFeature(ComboBasic.CONTAINMENT_PARENT+Containment.class.getName(), cf);
            cbasic.registerFeature(ComboBasic.CONTAINMENT_CHILD+Containment.class.getName(), crf);

            List<String> candidates = new ArrayList<>(this.freqFeature.getMapTerm2TTF().keySet());

            filterByTTF(candidates);

            List<JATETerm> terms = cbasic.execute(candidates);
            terms = cutoff(terms);

            addAdditionalTermInfo(terms, searcher, properties.getSolrFieldNameJATENGramInfo(),
                    properties.getSolrFieldNameID());
            LOG.info("Complete ComboBasic term extraction.");
            return terms;
        } finally {
            try {
                searcher.close();
            } catch (IOException e) {
                LOG.error(e.toString());
            }
        }
    }

}