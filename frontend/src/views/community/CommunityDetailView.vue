<template>
  <div class="page-container community-detail-page">
    <router-link class="community-back" to="/community">← 返回社区</router-link
    ><CommunityThread :id="id" @deleted="router.push('/community')" />
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommunityThread from '@/components/community/CommunityThread.vue'
import '@/assets/styles/community.css'
const route = useRoute(),
  router = useRouter()
const id = computed(() => Number(route.params.id))
</script>
