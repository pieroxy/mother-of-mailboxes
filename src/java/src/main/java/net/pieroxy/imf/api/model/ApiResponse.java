package net.pieroxy.imf.api.model;

public class ApiResponse<T> {
  private boolean ok;
  private String error;
  private T result;

  public static <T> ApiResponse<T> buildOkResult(T result) {
    ApiResponse<T> response = new ApiResponse<>();
    response.ok = true;
    response.result = result;
    return response;
  }

  public static <T> ApiResponse<T> buildErrResult(String error) {
    ApiResponse<T> response = new ApiResponse<>();
    response.ok = false;
    response.error = error;
    return response;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public T getResult() {
    return result;
  }

  public void setResult(T result) {
    this.result = result;
  }
}
