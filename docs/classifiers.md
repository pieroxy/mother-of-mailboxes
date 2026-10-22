# Spam classifiers

[← back to docs](README.md)

Three classifiers — subject, headers and body — learn what spam looks like from your own mail,
per account. Nothing is sent anywhere: the data and the models stay in `dataFolder`.

- Everything in the Spam folder is spam; everything in any other folder is legitimate mail,
  except `INBOX`, `mom-rules/` and the [excluded folders](#excluding-a-folder-from-the-corpus).
- Filing or archiving the mail you keep is what teaches them legitimate mail: mail left in
  `INBOX` teaches them nothing.

## Classifier corpus collection

When `classifierCorpusRetentionDays > 0`, MOM builds a labeled dataset per account under
`dataFolder`, walking every folder except `INBOX` and `mom-rules/` and labeling messages `SPAM`
(if in the `classifierSpamFolderName` folder) or `HAM` (everything else).

- The Spam folder is scanned every cycle, so spam you delete by hand is still captured first.
  Every other folder is scanned once a day.
- Only mail received in the last `classifierCorpusRetentionDays` days is collected, and older
  data is pruned once a day.

## Excluding a folder from the corpus

`classifierExcludedFolders` skips a folder (matched by name, anywhere in the tree) entirely —
neither `SPAM` nor `HAM`.

**If a classifier rule** ([`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md),
[`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md),
[`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md)) **moves mail to its own folder,
add that folder here.** Otherwise its content is collected as legitimate mail, and the
classifiers end up training on their own verdicts. Rules moving mail straight to the Spam folder
don't need this.

```json
{ "classifierExcludedFolders": ["SpamML"] }
```

## Subject classifier training

Once a day, after the full scan, MOM retrains a subject classifier on the collected corpus. It
needs **at least 50 examples of each class** (`SPAM` and `HAM`): until then, no model is written
and the matching rules do nothing. The web UI shows each account's progress.

Use [`SUBJECT_CLASSIFIER_EQUALS`](matchers/subject-classifier-equals.md) in a rule to act on it.

## Header classifier training

Same as the subject classifier, but on what the headers tell (sender and recipient domains,
mailing-list headers, `Reply-To`, attachments...). Same minimum: **50 examples of each class**.

Use [`HEADER_CLASSIFIER_EQUALS`](matchers/header-classifier-equals.md) in a rule to act on it.
The classifiers are independent: use any of them, alone or together.

## Body classifier training

Same as the other two, but on the text of the message body, as shown when it's opened. It needs
more data: **at least 150 examples of each class**.

Use [`BODY_CLASSIFIER_EQUALS`](matchers/body-classifier-equals.md) in a rule to act on it.
