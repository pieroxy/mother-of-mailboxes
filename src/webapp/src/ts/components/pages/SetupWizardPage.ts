import m from "mithril";
import { AbstractPage } from "./AbstractPage";
import { ApiEndpoints } from "../../auto/ApiEndpoints";
import { RecommendedReputationListDto } from "../../auto/pieroxy-mom";
import { Auth } from "../../utils/Auth";
import { Endpoints } from "../../utils/navigation/Endpoints";
import { Routing } from "../../utils/navigation/Routing";
import { WebServerControls } from "../WebServerControls";

const STEPS = ["Web server", "Storage", "Reputation lists"];

/**
 * First-start setup (see FirstStart): every page redirects here until it's done. Port/address
 * changes apply immediately, like on the settings page; the other steps are saved on "Next".
 * Finishing ends the setup and opens the account creation wizard.
 */
export class SetupWizardPage extends AbstractPage {
  private step = 0;
  private loading = true;
  private saving = false;
  private error: string | undefined;

  private webServer = new WebServerControls(true, 0, "");
  private dataFolder = "";
  private keepLogFiles = 0;
  private recommendedLists: RecommendedReputationListDto[] = [];
  private selectedListIds = new Set<string>();

  getPageTitle(): string {
    return "MOM - Setup";
  }

  oninit() {
    Promise.all([ApiEndpoints.GeneralSettings.call({}), ApiEndpoints.RecommendedReputationLists.call({})])
      .then(([settings, recommended]) => {
        this.webServer = new WebServerControls(settings.webServerEnabled, settings.webServerHttpPort, settings.webServerAddress || "");
        this.dataFolder = settings.dataFolder;
        this.keepLogFiles = settings.keepLogFiles;
        this.recommendedLists = recommended.lists;
        this.selectedListIds = new Set(recommended.lists.map((list) => list.id));
        this.loading = false;
        m.redraw();
      })
      .catch((err: Error) => {
        this.loading = false;
        this.error = err.message;
        m.redraw();
      });
  }

  render(): m.Children {
    return m("page.setupwizardpage", [
      m(".page-header", m("h1.page-title", "Welcome to MOM")),
      m(".wizard-steps", STEPS.map((title, index) =>
        m(".wizard-step" + (index === this.step ? ".current" : index < this.step ? ".done" : ""), (index + 1) + ". " + title))),
      this.error ? m(".settings-error.errorMessage", this.error) : null,
      this.loading ? m(".page-loading", "Loading…") : m(".page-card.edit-form", [this.renderStep(), this.renderActions()]),
    ]);
  }

  private renderStep(): m.Children {
    switch (this.step) {
      case 0:
        return [
          m("p", "A few settings first, then you'll add your first mail account. Every value below already has a sensible default."),
          this.webServer.render(false),
          m("p.field-hint", "Port and address changes apply right away. You'll also be able to disable the web server later on, from the general settings."),
        ];
      case 1:
        return [
          this.field("Data folder", m("input", {
            type: "text", value: this.dataFolder,
            oninput: (e: Event) => (this.dataFolder = (e.target as HTMLInputElement).value),
          }), "Where MOM keeps its state, models and logs. Absolute, or relative to the folder holding config.json."),
          this.field("Keep log files (days, 0 = no rotation)", m("input", {
            type: "number", value: this.keepLogFiles, min: 0,
            oninput: (e: Event) => (this.keepLogFiles = Number((e.target as HTMLInputElement).value)),
          })),
        ];
      default:
        return [
          m("p", "Lists of known spam sources, downloaded once a day. The account wizard creates its spam rules for the ones selected here."),
          m(".setup-lists", this.recommendedLists.map((list) => m("label.setup-list", { key: list.id }, [
            m("input", {
              type: "checkbox", checked: this.selectedListIds.has(list.id),
              onchange: (e: Event) => this.toggleList(list.id, (e.target as HTMLInputElement).checked),
            }),
            m(".setup-list-text", [
              m(".setup-list-title", [list.id, m("span.setup-list-size", " · " + list.approxEntries)]),
              m(".setup-list-description", list.description),
            ]),
          ]))),
        ];
    }
  }

  private renderActions(): m.Children {
    const last = this.step === STEPS.length - 1;
    return m(".edit-actions", [
      this.step > 0 ? m("button.cancel-button", { onclick: () => this.goTo(this.step - 1), disabled: this.saving }, "Back") : null,
      m("button.ok-button", { onclick: () => this.next(), disabled: this.saving }, this.saving ? "Saving…" : last ? "Finish" : "Next"),
    ]);
  }

  private field(label: string, control: m.Children, hint?: string): m.Children {
    return m(".edit-field", [m("label", label), control, hint ? m("span.field-hint", hint) : null]);
  }

  private toggleList(id: string, checked: boolean) {
    if (checked) this.selectedListIds.add(id);
    else this.selectedListIds.delete(id);
  }

  private goTo(step: number) {
    this.error = undefined;
    this.step = step;
  }

  private next() {
    if (this.step === 0) {
      this.goTo(1);
      return;
    }
    if (this.step === 1) {
      if (!this.dataFolder.trim()) {
        this.error = "The data folder must not be blank.";
        return;
      }
      if (this.keepLogFiles < 0) {
        this.error = "Keep log files must be zero (no rotation) or a positive number of days.";
        return;
      }
      this.save(ApiEndpoints.SaveSetupStorage.call({ dataFolder: this.dataFolder.trim(), keepLogFiles: this.keepLogFiles }), () => this.goTo(2));
      return;
    }
    this.save(ApiEndpoints.CompleteSetup.call({ reputationListIds: Array.from(this.selectedListIds) }), () => {
      Auth.setSetupInProgress(false);
      Routing.goToScreen(Endpoints.ACCOUNT_CREATE);
    });
  }

  private save(call: Promise<unknown>, then: () => void) {
    this.saving = true;
    this.error = undefined;
    call
      .then(() => {
        this.saving = false;
        then();
        m.redraw();
      })
      .catch((err: Error) => {
        this.saving = false;
        this.error = err.message;
        m.redraw();
      });
  }
}
