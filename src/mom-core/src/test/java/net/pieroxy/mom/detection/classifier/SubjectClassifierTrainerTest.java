package net.pieroxy.mom.detection.classifier;

import net.pieroxy.mom.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.mom.detection.classifier.ClassifierExample;
import net.pieroxy.mom.detection.classifier.ClassifierLabel;
import net.pieroxy.mom.detection.classifier.SubjectClassifierTrainer;
import opennlp.tools.doccat.DoccatModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubjectClassifierTrainerTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static ClassifierExample example(String subject, ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setSubject(subject);
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }

  @Test
  public void skipsTrainingWhenNotEnoughExamplesOfEachClass() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      examples.add(example("spam subject " + i, ClassifierLabel.SPAM));
      examples.add(example("ham subject " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    assertFalse("not enough examples (5 < " + SubjectClassifierTrainer.MIN_EXAMPLES_PER_CLASS
        + " per class): no model should be written", store.getModelFile().exists());
  }

  @Test
  public void savesTrainingCountsEvenWhenTrainingIsSkipped() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      examples.add(example("spam subject " + i, ClassifierLabel.SPAM));
      examples.add(example("ham subject " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    ClassifierTrainingCounts counts = store.loadTrainingCounts();
    assertEquals(5, counts.getSpamCount());
    assertEquals(5, counts.getHamCount());
  }

  @Test
  public void skipsTrainingWhenOneClassIsMissingEntirely() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      examples.add(example("spam subject " + i, ClassifierLabel.SPAM)); // lots of spam, no ham
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    assertFalse("a high total isn't enough: the minimum is required for BOTH classes", store.getModelFile().exists());
  }

  @Test
  public void writesAValidModelFileOnceMature() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < SubjectClassifierTrainer.MIN_EXAMPLES_PER_CLASS + 5; i++) {
      examples.add(example("buy cheap stuff now " + i, ClassifierLabel.SPAM));
      examples.add(example("weekly team meeting notes " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    File modelFile = store.getModelFile();
    assertTrue(modelFile.isFile());
    assertTrue("the temp file must not linger after a successful atomic rename",
        !new File(modelFile.getParentFile(), modelFile.getName() + ".tmp").exists());
    new DoccatModel(modelFile); // must not throw: the written file must be a valid model
  }
}
