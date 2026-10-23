// Only an explicit AI capacity error is an expected refusal. Unknown results fail closed.
export function classify(mode, response) {
  if (response.status !== 200) return 'failure';
  if (mode !== 'ask') {
    try { return JSON.parse(response.body).code === 0 ? 'success' : 'failure'; }
    catch (_) { return 'failure'; }
  }
  try {
    const value = JSON.parse(response.body);
    return value.code === 1007 ? 'rejected' : 'failure';
  } catch (_) { /* SSE is parsed below. */ }
  const frames = response.body.replace(/\r\n/g, '\n').split('\n\n').filter(Boolean);
  let done = false;
  const errors = [];
  for (const frame of frames) {
    const lines = frame.split('\n');
    const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim();
    if (!event && lines.some(line => line.startsWith('data:'))) return 'failure';
    if (event === 'done') done = true;
    if (event === 'error') {
      try { errors.push(JSON.parse(lines.filter(line => line.startsWith('data:')).map(line => line.slice(5)).join('\n')).code); }
      catch (_) { return 'failure'; }
    }
  }
  if (errors.length) return !done && errors.every(code => code === 1007) ? 'rejected' : 'failure';
  return done ? 'success' : 'failure';
}
