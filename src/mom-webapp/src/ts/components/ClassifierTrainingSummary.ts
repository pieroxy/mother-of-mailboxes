import m from "mithril";
import { ClassifierTrainingDto, ClassifierTrainingStateDto } from "../auto/pieroxy-mom";
import { StatusOkIcon } from "./atoms/icons/StatusOkIcon";
import { StatusInfoIcon } from "./atoms/icons/StatusInfoIcon";

interface ClassifierTrainingSummaryAttrs {
  training: ClassifierTrainingStateDto;
}

/**
 * Compact per-account summary of the three spam classifiers (see AccountsApi's
 * ClassifierTrainingStateDto): how many labeled examples the account's corpus holds so far, and
 * each classifier's trained/pending status against its own threshold. spamExamples/hamExamples
 * are shared by all three (they train on the same corpus) — a classifier is still "pending" until
 * both counts clear its own minExamplesPerClass.
 */
export class ClassifierTrainingSummary implements m.ClassComponent<ClassifierTrainingSummaryAttrs> {
  view({ attrs }: m.Vnode<ClassifierTrainingSummaryAttrs>): m.Children {
    const training = attrs.training;
    if (!training) return null;

    const collected = Math.min(training.spamExamples, training.hamExamples);

    return m(".classifier-training", [
      m(".classifier-training-counts", training.spamExamples + " spam / " + training.hamExamples + " ham example(s) collected"),
      m(".classifier-training-models", [
        m(ClassifierBadge, { label: "Subject", state: training.subject, collected }),
        m(ClassifierBadge, { label: "Header", state: training.header, collected }),
        m(ClassifierBadge, { label: "Body", state: training.body, collected }),
      ]),
    ]);
  }
}

interface ClassifierBadgeAttrs {
  label: string;
  state: ClassifierTrainingDto;
  collected: number;
}

class ClassifierBadge implements m.ClassComponent<ClassifierBadgeAttrs> {
  view({ attrs }: m.Vnode<ClassifierBadgeAttrs>): m.Children {
    const { label, state, collected } = attrs;
    const title = state.trained
      ? "Trained " + state.lastTrainedTimestamp
      : "Needs " + state.minExamplesPerClass + " spam and " + state.minExamplesPerClass + " ham examples to start training";

    return m(".classifier-badge" + (state.trained ? ".classifier-badge-trained" : ".classifier-badge-pending"), { title }, [
      m(state.trained ? StatusOkIcon : StatusInfoIcon),
      m("span.classifier-badge-label", label),
      m("span.classifier-badge-status", state.trained ? "trained" : collected + "/" + state.minExamplesPerClass),
    ]);
  }
}
