import m from "mithril";

/** The label shared by every page editing keepLogFiles. */
export const KEEP_LOG_FILES_LABEL = "Keep log files (days; empty = 30, 0 = forever)";

interface KeepLogFilesInputAttrs {
  /** null: unset, i.e. the default (see LogRetention on the backend). */
  value: number | null;
  onChange: (value: number | null) => void;
}

/** The top-level keepLogFiles setting, for the application log and the access log alike. */
export class KeepLogFilesInput implements m.ClassComponent<KeepLogFilesInputAttrs> {
  view({ attrs }: m.Vnode<KeepLogFilesInputAttrs>): m.Children {
    return m("input", {
      type: "number", min: 0, value: attrs.value ?? "", placeholder: "30 (default)",
      title: "Days of application and access logs to keep. 0 keeps everything.",
      oninput: (e: Event) => {
        const raw = (e.target as HTMLInputElement).value.trim();
        attrs.onChange(raw === "" ? null : Number(raw));
      },
    });
  }
}

/** Mirrors LogRetention#validate on the backend. */
export function validateKeepLogFiles(value: number | null): string | undefined {
  if (value !== null && (!Number.isInteger(value) || value < 0)) return "Keep log files must be empty (30 days), 0 (forever) or a positive number of days.";
  return undefined;
}
