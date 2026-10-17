import type { CodeListing } from '@/viz/engine/types'

/**
 * The simulator's own listing with English comments. Line numbers match the
 * scene's listing one for one, because every step points at a line by number.
 */
export const REVERSE_CODE: CodeListing = {
  file: 'reverse.c',
  lines: [
    'void Reverse(LinkList L) {',
    '    LNode *prev = NULL, *cur = L->next, *nxt;',
    '    while (cur != NULL) {',
    '        nxt = cur->next;   // keep the rest',
    '        cur->next = prev;  // turn the link',
    '        prev = cur;        // prev steps on',
    '        cur = nxt;         // cur steps on',
    '    }',
    '    L->next = prev;        // reattach the head',
    '}',
  ],
  vars: { L: 'graphite', prev: 'indigo', cur: 'blue', nxt: 'orange' },
}
