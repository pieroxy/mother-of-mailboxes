export interface ApiResponse<T> {
  ok: boolean;
  error?: string;
  result?: T;
}

export interface ApiCallOptions<I> {
  method: "GET" | "POST";
  endpoint: string;
  body: I;
}

export class Api {
  static async call<I, O>(options: ApiCallOptions<I>): Promise<O> {
    const url = "/api/" + options.endpoint;
    const response = options.method === "GET"
      ? await fetch(url + "?input=" + encodeURIComponent(JSON.stringify(options.body)))
      : await fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(options.body),
        });
    const payload: ApiResponse<O> = await response.json();
    if (!payload.ok) {
      throw new Error(payload.error ?? ("API call to " + options.endpoint + " failed."));
    }
    return payload.result as O;
  }
}
