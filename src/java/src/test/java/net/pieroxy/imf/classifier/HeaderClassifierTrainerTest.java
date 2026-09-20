package net.pieroxy.imf.classifier;

import net.pieroxy.imf.detection.classifier.ClassifierCorpusStore;
import net.pieroxy.imf.detection.classifier.ClassifierExample;
import net.pieroxy.imf.detection.classifier.ClassifierLabel;
import net.pieroxy.imf.detection.classifier.HeaderClassifierTrainer;
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

public class HeaderClassifierTrainerTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static ClassifierExample example(ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }

  private static ClassifierExample spamLikeExample() {
    ClassifierExample e = example(ClassifierLabel.SPAM);
    e.setFrom(List.of("deals@spammy.example.net"));
    e.setPrecedence("bulk");
    e.setListUnsubscribePresent(true);
    e.setReturnPathMismatch(true);
    return e;
  }

  private static ClassifierExample hamLikeExample() {
    ClassifierExample e = example(ClassifierLabel.HAM);
    e.setFrom(List.of("alice@example.com"));
    e.setReply(true);
    e.setReturnPathMismatch(false);
    return e;
  }

  @Test
  public void skipsTrainingWhenNotEnoughExamplesOfEachClass() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      examples.add(spamLikeExample());
      examples.add(hamLikeExample());
    }
    store.append(LocalDate.now(), examples);

    new HeaderClassifierTrainer(store).train();

    assertFalse("not enough examples (5 < " + HeaderClassifierTrainer.MIN_EXAMPLES_PER_CLASS
        + " per class): no model should be written", store.getHeaderModelFile().exists());
  }

  @Test
  public void skipsTrainingWhenOneClassIsMissingEntirely() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      examples.add(spamLikeExample()); // lots of spam, no ham
    }
    store.append(LocalDate.now(), examples);

    new HeaderClassifierTrainer(store).train();

    assertFalse("a high total isn't enough: the minimum is required for BOTH classes", store.getHeaderModelFile().exists());
  }

  @Test
  public void writesAValidModelFileOnceMature() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < HeaderClassifierTrainer.MIN_EXAMPLES_PER_CLASS + 5; i++) {
      examples.add(spamLikeExample());
      examples.add(hamLikeExample());
    }
    store.append(LocalDate.now(), examples);

    new HeaderClassifierTrainer(store).train();

    File modelFile = store.getHeaderModelFile();
    assertTrue(modelFile.isFile());
    assertTrue("the temp file must not linger after a successful atomic rename",
        !new File(modelFile.getParentFile(), modelFile.getName() + ".tmp").exists());
    new DoccatModel(modelFile); // must not throw: the written file must be a valid model
  }

  @Test
  public void doesNotWriteToTheSubjectModelFile() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < HeaderClassifierTrainer.MIN_EXAMPLES_PER_CLASS + 5; i++) {
      examples.add(spamLikeExample());
      examples.add(hamLikeExample());
    }
    store.append(LocalDate.now(), examples);

    new HeaderClassifierTrainer(store).train();

    assertFalse("the header classifier must never touch the subject model's own file",
        store.getModelFile().exists());
  }
}
