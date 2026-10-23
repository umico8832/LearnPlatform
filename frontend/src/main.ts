import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setAuthRedirectHandler } from './utils/authNavigation'
import { setErrorNotifier } from './utils/notify'
import LearnerToastHost from './components/layout/LearnerToastHost.vue'
import { showErrorToast } from './components/layout/learnerToasts'
import { registerUiComponents } from './components/ui'
import './assets/styles/global.css'
import './assets/styles/living.css'
import './assets/styles/legacy-dark.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
registerUiComponents(app)

setAuthRedirectHandler((redirect) => {
  void router.push({ path: '/login', query: redirect ? { redirect } : undefined })
})

setErrorNotifier(showErrorToast)

app.mount('#app')

const toastRoot = document.createElement('div')
document.body.append(toastRoot)
createApp(LearnerToastHost).mount(toastRoot)
