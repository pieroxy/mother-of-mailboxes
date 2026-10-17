package net.pieroxy.mom.detection.classifier;

import net.pieroxy.mom.detection.classifier.BodyClassifierTrainer;
import net.pieroxy.mom.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.mom.detection.classifier.ClassifierExample;
import net.pieroxy.mom.detection.classifier.ClassifierLabel;
import opennlp.tools.doccat.DoccatModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BodyClassifierTrainerTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static ClassifierExample example(String bodyText, ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setBodyText(bodyText);
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
      examples.add(example("spam body " + i, ClassifierLabel.SPAM));
      examples.add(example("ham body " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new BodyClassifierTrainer(store).train();

    assertFalse("not enough examples (5 < " + BodyClassifierTrainer.MIN_EXAMPLES_PER_CLASS
        + " per class): no model should be written", store.getBodyModelFile().exists());
  }

  @Test
  public void skipsTrainingWhenOneClassIsMissingEntirely() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      examples.add(example("spam body " + i, ClassifierLabel.SPAM)); // lots of spam, no ham
    }
    store.append(LocalDate.now(), examples);

    new BodyClassifierTrainer(store).train();

    assertFalse("a high total isn't enough: the minimum is required for BOTH classes", store.getBodyModelFile().exists());
  }

  @Test
  public void writesAValidModelFileOnceMature() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < BodyClassifierTrainer.MIN_EXAMPLES_PER_CLASS + 5; i++) {
      examples.add(example("buy cheap stuff now click here " + i, ClassifierLabel.SPAM));
      examples.add(example("weekly team meeting notes attached " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new BodyClassifierTrainer(store).train();

    File modelFile = store.getBodyModelFile();
    assertTrue(modelFile.isFile());
    assertTrue("the temp file must not linger after a successful atomic rename",
        !new File(modelFile.getParentFile(), modelFile.getName() + ".tmp").exists());
    new DoccatModel(modelFile); // must not throw: the written file must be a valid model
  }

  @Test
  public void examplesWithNoBodyTextAtAllAreStillUsedForTraining() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < BodyClassifierTrainer.MIN_EXAMPLES_PER_CLASS + 5; i++) {
      examples.add(example(null, ClassifierLabel.SPAM)); // attachment-only spam, no body text
      examples.add(example("weekly team meeting notes attached " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new BodyClassifierTrainer(store).train();

    assertTrue("bodyEmpty is itself a feature: examples without body text must not be skipped",
        store.getBodyModelFile().isFile());
  }

  @Test
  public void doesNotWriteToTheSubjectOrHeaderModelFiles() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < BodyClassifierTrainer.MIN_EXAMPLES_PER_CLASS + 5; i++) {
      examples.add(example("buy cheap stuff now click here " + i, ClassifierLabel.SPAM));
      examples.add(example("weekly team meeting notes attached " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new BodyClassifierTrainer(store).train();

    assertFalse("the body classifier must never touch the subject model's own file", store.getModelFile().exists());
    assertFalse("the body classifier must never touch the header model's own file", store.getHeaderModelFile().exists());
  }
}
