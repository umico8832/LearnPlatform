import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import App from './App.vue'
import router from './router'
import { setAuthRedirectHandler } from '@/utils/authNavigation'
import { setErrorNotifier } from '@/utils/notify'
import '@/assets/styles/global.css'
import './styles.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)

setAuthRedirectHandler((redirect) => {
  void router.push({ name: 'AdminLogin', query: redirect ? { redirect } : undefined })
})

setErrorNotifier((message) => {
  ElMessage.error(message)
})

app.mount('#admin-app')
