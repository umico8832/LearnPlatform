import type { Page } from '@playwright/test'

const prepared = new WeakSet<Page>()

// Only the external SDK is replaced. The real backend still submits this official
// dummy token to Cloudflare Siteverify with the isolated environment's test key.
export async function prepareTurnstile(page: Page) {
  if (prepared.has(page)) return
  await page.route('https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit', (route) =>
    route.fulfill({
      contentType: 'application/javascript',
      body: `
        const widgets = new Map();
        let sequence = 0;
        function verify(id) {
          queueMicrotask(() => {
            const options = widgets.get(id);
            if (!options) return;
            if (options.sitekey !== '1x00000000000000000000AA') {
              options['error-callback']?.('110100');
              return;
            }
            options.callback('XXXX.DUMMY.TOKEN.XXXX');
          });
        }
        window.turnstile = {
          render(container, options) {
            const id = 'e2e-' + sequence++;
            widgets.set(id, options);
            verify(id);
            return id;
          },
          reset(id) { verify(id); },
          remove(id) { widgets.delete(id); },
        };
      `,
    }),
  )
  prepared.add(page)
}
