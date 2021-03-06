package feature;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import utils.Constants;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class FeaturesExtractor {

	private HashSet<String> stopwords;
	private static String[] s_stopwords = { "DT", "CD", "LS", "CC", "EX", "IN",
			"MD", "PDT", "PRP", "PRP$", "RB", "RBR", "RBS", "RP", "POS", "SYM",
			"TO", "UH", "WP", "WP$", "WRB", "WDT", "#", "$", "\"", "(", ")",
			",", ".", ":", "''", "LRB", "RRB", "LCB", "RCB", "LSB", "RSB",
			"-LRB-", "B-", "``", "FW", "-RRB-", " ", " " };

	private String documentName;
	private int totalNumberOfWordsInDocument = 0;
	private int totalNumberOfConceptsInDocument = 0;

	private Morphology morphology = new Morphology();
	private MaxentTagger tagger;
	private final String WORDNET_PATH = "WordNet-3/dict";

	private Map<String, Integer> termFrequency = new HashMap<>();
	private Map<String, Integer> conceptFrequency = new HashMap<>();

	public FeaturesExtractor(String pDocumentName, MaxentTagger tagger) {

		stopwords = new HashSet<String>(s_stopwords.length);
		for (String stopWord : s_stopwords) {
			stopwords.add(stopWord);
		}
		this.documentName = pDocumentName;
		this.tagger = tagger;
	}

	public DocumentVector getDocumentVector(String document) throws IOException {

		URL url = new URL("file", null, WORDNET_PATH);
		IDictionary dict = new Dictionary(url);
		dict.open();

		document = tagger.tagString(document);

		String[] splittedDocument = document.split(" ");
		String originalWord, pos;

		for (int i = 0; i < splittedDocument.length; i++) {

			String[] temp = splittedDocument[i].split("_");

			originalWord = temp[0];
			pos = temp[1];

			if (!isStopWord(pos)) {

				originalWord = morphology.stem(originalWord);

				String stemmedWord = morphology.lemma(originalWord, pos, true);

				if (termFrequency.containsKey(stemmedWord)) {
					termFrequency.put(stemmedWord,
							termFrequency.get(stemmedWord) + 1);
					totalNumberOfWordsInDocument++;
				} else {
					termFrequency.put(stemmedWord, 1);
					totalNumberOfWordsInDocument++;

					NumberOfDocumentsWhereWordAppears
							.updateNumberOfDocumentsWhereWordAppears(stemmedWord);
				}

				// adding synonyms
				if (Constants.CONCEPTS) {
					ArrayList<String> wordSynset = getSynsets(dict,
							stemmedWord, getPos(pos));
					if (wordSynset != null && !wordSynset.isEmpty()) {

						for (String string : wordSynset) {
							if (this.conceptFrequency.containsKey(string)) {
								this.conceptFrequency.put(string,
										this.conceptFrequency.get(string) + 1);
								totalNumberOfConceptsInDocument++;

							} else {
								this.conceptFrequency.put(string, 1);
								NumberOfDocumentsWhereWordAppears
										.updateNumberOfDocumentsWhereWordAppears(string);
								totalNumberOfConceptsInDocument++;
							}
						}
					}
				}

			}
		}

		if (Constants.CUT_ON_FREQUENCY) {
			cutOnThreshold(termFrequency, totalNumberOfWordsInDocument);
			cutOnThreshold(conceptFrequency, totalNumberOfConceptsInDocument);
		}

		dict.close();
		return new DocumentVector(documentName, totalNumberOfWordsInDocument,
				totalNumberOfConceptsInDocument, termFrequency,
				conceptFrequency);
	}

	private void cutOnThreshold(Map<String, Integer> frequencyMap, int total) {
		int threshold = (int) Math.round(Constants.LOWER_LIMIT * total / 100);

		for (Iterator<Map.Entry<String, Integer>> iterator = frequencyMap
				.entrySet().iterator(); iterator.hasNext();) {

			Map.Entry<String, Integer> entry = iterator.next();

			if (entry.getValue() < threshold) {
				iterator.remove();

				NumberOfDocumentsWhereWordAppears
						.removeWordFromDocumentCorpus(entry.getKey());
			}
		}
	}

	private ArrayList<String> getSynsets(IDictionary dictionary, String word,
			POS pos) {
		ArrayList<String> allSynsets = new ArrayList<>();
		IIndexWord indexWord = dictionary.getIndexWord(word, pos);
		String synonym;
		int synsetSize;

		if (indexWord != null) {
			IWordID wordID = indexWord.getWordIDs().get(0);
			IWord iWord = dictionary.getWord(wordID);
			ISynset synset = iWord.getSynset();

			synsetSize = synset.getWords().size();
			for (int i = 0; i < synsetSize
					&& i < Constants.MAXIMUM_SYNSETS_NUMBER; i++) {
				synonym = synset.getWords().get(i).getLemma().toLowerCase();
				if (!synonym.equals(word) && synonym.length() > 3) {
					allSynsets.add(synonym);
				}
			}
		}
		return allSynsets;
	}

	private POS getPos(String pos) {
		if (pos.equals("NN") || pos.equals("NNS") || pos.equals("NNP")
				|| pos.equals("NNPS")) {
			return POS.NOUN;
		} else if (pos.equals("VB") || pos.equals("VBD") || pos.equals("VBG")
				|| pos.equals("VBN") || pos.equals("VBP") || pos.equals("VBZ")) {
			return POS.VERB;
		} else if (pos.equals("JJ") || pos.equals("JJR") || pos.equals("JJS")) {
			return POS.ADJECTIVE;
		}
		System.out.println("Equivalent POS not found! Default POS.NOUN used!");
		return POS.NOUN;
	}

	private boolean isStopWord(String aWord) {
		return stopwords.contains(aWord);
	}

}
