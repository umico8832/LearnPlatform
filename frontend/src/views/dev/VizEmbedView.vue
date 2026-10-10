<script setup lang="ts">
import { useLivingTheme } from '@/composables/useLivingTheme'
import { VizPlayer, type SceneSpec } from '@/viz'

/** 开发预览：可视化以嵌入尺寸出现在一段讲解里，用题目自己的数据，可展开为整页。M2 题目理解页接手后删除。 */
useLivingTheme()

const examples: { title: string; spec: SceneSpec; query: Record<string, string>; scene: string }[] = [
  {
    title: '用本题数据逆置 3 → 1 → 4',
    scene: 'linked-list',
    spec: { scene: 'linked-list', op: 'reverse', values: [3, 1, 4] },
    query: { op: 'reverse', values: '3,1,4' },
  },
  {
    title: '出栈序列 3 1 2 为什么不合法',
    scene: 'stack',
    spec: { scene: 'stack', op: 'check', seq: [3, 1, 2] },
    query: { op: 'check', seq: '3,1,2' },
  },
]
</script>

<template>
  <main class="embed-page">
    <article class="embed-article">
      <h1>嵌入式可视化</h1>
      <p>
        讲解里的动画用题目自己的数据播放，在容易出错的那一步停下来先让你预测。需要细看代码和变量时，点“展开”进入整页。
      </p>
      <VizPlayer
        v-for="example in examples"
        :key="example.scene"
        class="embed-player"
        :title="example.title"
        :spec="example.spec"
        :expand-to="{ name: 'Visualization', params: { scene: example.scene }, query: example.query }"
      />
    </article>
  </main>
</template>

<style scoped>
.embed-page {
  min-height: 100vh;
  padding: var(--lt-space-9) var(--lt-space-7);
}

.embed-article {
  max-width: 760px;
  margin: 0 auto;
}

.embed-article h1 {
  margin: 0;
  font-size: var(--lt-text-title1);
}

.embed-article p {
  margin: var(--lt-space-4) 0 var(--lt-space-6);
  color: var(--lt-label-2);
  font-size: var(--lt-text-reading);
  line-height: var(--lt-leading-reading);
}

.embed-player + .embed-player {
  margin-top: var(--lt-space-6);
}
</style>
