<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { LtButton, LtTextField } from '@/components/living'
import { globalSearch, type SearchItem } from '@/api/search'

const keyword = ref('')
const results = ref<SearchItem[]>([])
const loading = ref(false)
const searched = ref(false)
const error = ref('')
let generation = 0
onBeforeUnmount(() => generation++)
async function search() {
  const current = ++generation
  results.value = []
  error.value = ''
  searched.value = false
  const query = keyword.value.trim()
  if (!query) return
  loading.value = true
  try {
    const response = await globalSearch(query, 20)
    if (current !== generation) return
    results.value = response.data.questions
    searched.value = true
  } catch {
    if (current === generation) error.value = '搜索暂时不可用，请重试。'
  } finally {
    if (current === generation) loading.value = false
  }
}
</script>
<template>
  <section class="search" aria-label="搜索题目">
    <form @submit.prevent="search">
      <LtTextField v-model="keyword" label="搜索题目" placeholder="输入题干关键词" />
      <LtButton type="submit" :disabled="loading">{{ loading ? '正在搜索…' : '搜索' }}</LtButton>
    </form>
    <p v-if="loading" role="status">正在搜索题目…</p>
    <div v-else-if="error" role="alert">
      <p>{{ error }}</p>
      <LtButton @click="search">重试搜索</LtButton>
    </div>
    <template v-else-if="searched">
      <p v-if="!results.length" role="status">没有找到相关题目，换个关键词再试。</p>
      <template v-else
        ><p>选择一道题查看，最多显示 20 条匹配结果。</p>
        <ul>
          <li v-for="item in results" :key="item.id">
            <RouterLink :to="item.link">{{ item.title }}</RouterLink
            ><span>{{ item.subtitle }}</span>
          </li>
        </ul></template
      >
    </template>
  </section>
</template>
<style scoped>
.search {
  padding: var(--lt-space-5);
  border-radius: var(--lt-radius-xl);
  background: var(--lt-surface);
}
form {
  display: flex;
  align-items: flex-end;
  gap: var(--lt-space-4);
}
form > :first-child {
  flex: 1;
}
p,
li span {
  color: var(--lt-label-2);
  font-size: var(--lt-text-footnote);
}
ul {
  display: grid;
  gap: var(--lt-space-3);
  padding: 0;
  list-style: none;
}
li {
  display: grid;
  gap: var(--lt-space-1);
}
a {
  color: var(--lt-accent);
  text-underline-offset: var(--lt-space-1);
}
</style>
