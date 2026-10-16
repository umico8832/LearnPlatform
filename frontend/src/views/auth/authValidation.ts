// Preserve the email and Unicode length rules used by the previous Element Plus forms (async-validator).
const emailPattern =
  /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]+\.)+[a-zA-Z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]{2,}))$/

export function validEmail(value: string) {
  return value.length <= 320 && emailPattern.test(value)
}

export function validLength(value: string, min: number, max: number) {
  const length = Array.from(value).length
  return length >= min && length <= max
}
