<template>
  <AuthLayout alternate-to="/register" alternate-text="创建账号">
    <div class="auth-card-header"><h1 id="auth-title">欢迎回来</h1><p>使用用户名或已验证邮箱继续学习</p></div>
    <el-form ref="formRef" class="auth-form" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
      <el-form-item label="用户名或邮箱" prop="account"><el-input v-model="form.account" :prefix-icon="User" placeholder="请输入用户名或邮箱" autocomplete="username" /></el-form-item>
      <el-form-item label="密码" prop="password"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password placeholder="请输入密码" autocomplete="current-password" /></el-form-item>
      <div class="form-row-between"><span>登录受到安全验证保护</span><router-link class="auth-inline-link" to="/forgot-password">忘记密码？</router-link></div>
      <el-form-item><TurnstileWidget ref="turnstileRef" v-model="form.turnstileToken" @expired="form.turnstileToken=''" /></el-form-item>
      <el-form-item><el-button native-type="submit" type="primary" class="auth-primary" :loading="loading" :disabled="loading || !form.turnstileToken">登录</el-button></el-form-item>
    </el-form>
    <div class="auth-footer">还没有账号？ <router-link to="/register">免费注册</router-link></div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import TurnstileWidget from '@/components/auth/TurnstileWidget.vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import '@/assets/styles/auth.css'

const router=useRouter(), route=useRoute(), userStore=useUserStore()
const formRef=ref<FormInstance>(), turnstileRef=ref<{reset:()=>void}>(), loading=ref(false)
const form=reactive({account:'',password:'',turnstileToken:''})
const rules:FormRules={account:[{required:true,message:'请输入用户名或邮箱',trigger:'blur'}],password:[{required:true,message:'请输入密码',trigger:'blur'}]}
async function handleLogin(){if(!await formRef.value?.validate().catch(()=>false)||!form.turnstileToken)return;loading.value=true;try{const res=await login(form);userStore.setLoginInfo(res.data.token,res.data.user);ElMessage.success('登录成功');await router.push((route.query.redirect as string)||'/')}catch{turnstileRef.value?.reset()}finally{loading.value=false}}
</script>
