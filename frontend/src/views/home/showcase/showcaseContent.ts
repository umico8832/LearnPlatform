import type { Component } from 'vue'
import { BookOpen, CalendarClock, ClipboardCheck, Compass, History, MessagesSquare, Play, Repeat } from '@lucide/vue'

/** Homepage copy and navigation. Features not built yet point at the placeholder pages. */

export interface MenuItem {
  title: string
  text: string
  to: string
  icon: Component
}

export interface NavMenu {
  id: string
  label: string
  items: MenuItem[]
}

export interface NavLink {
  label: string
  to: string
}

export const menus: NavMenu[] = [
  {
    id: 'product',
    label: 'Product',
    items: [
      { title: 'Visualizations', text: 'Watch the process, step by step', to: '/viz/binary-tree', icon: Play },
      { title: 'AI Tutor', text: 'Ask in your own words', to: '/product', icon: MessagesSquare },
      { title: 'Practice', text: 'Variants without the hints', to: '/product', icon: Repeat },
      { title: 'Review', text: 'Spaced around what you recall', to: '/product', icon: CalendarClock },
      { title: 'Exams', text: 'Timed papers, quiet until the end', to: '/product', icon: ClipboardCheck },
    ],
  },
  {
    id: 'learn',
    label: 'Learn',
    items: [
      { title: 'How it works', text: 'From a wrong answer to understanding', to: '/learning', icon: Compass },
      { title: 'Resources', text: 'Guides and course notes', to: '/resources', icon: BookOpen },
      { title: 'Changelog', text: 'What shipped lately', to: '/roadmap', icon: History },
    ],
  },
]

export const links: NavLink[] = [
  { label: 'Courses', to: '/courses' },
  { label: 'Roadmap', to: '/roadmap' },
  { label: 'About', to: '/about' },
]

export type HeroWord = 'understand' | 'see it' | 'remember' | 'explain'
export const heroWords: HeroWord[] = ['understand', 'see it', 'remember', 'explain']

/** Counted from the repository on 2026-10-17; update them when the project grows. */
export const facts = [
  { value: '2,100+', label: 'automated tests', note: 'unit, integration and browser' },
  { value: '3', label: 'live visualizations', note: 'binary tree, linked list, stack' },
  { value: '27', label: 'reviewed questions', note: 'in the first course' },
  { value: '3-step', label: 'hint ladder', note: 'on reviewed learning questions' },
  { value: 'FSRS', label: 'review scheduling', note: 'from unaided answers only' },
  { value: '0', label: 'lines of model code run', note: 'scenes are data, not code' },
]
