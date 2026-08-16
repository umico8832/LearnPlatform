import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import App from './App.vue'
import router from './router'
import { setAuthRedirectHandler } from './utils/authNavigation'
import { registerUiComponents } from './components/ui'
import './assets/styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
registerUiComponents(app)

setAuthRedirectHandler((redirect) => {
  void router.push({ path: '/login', query: redirect ? { redirect } : undefined })
})

app.mount('#app')
