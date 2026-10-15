<script setup lang="ts">
import {ref,watch} from 'vue';
const props=defineProps<{id:string;version?:string|number}>();const read=ref(false),error=ref('');
const key=()=>`huankan:event-read:${props.id}`;
const version=()=>{const v=String(props.version??'1');return /^\d{4}-\d{2}-\d{2}T/.test(v)&&!Number.isNaN(Date.parse(v))?new Date(v).toISOString():v;};
watch(()=>[props.id,props.version],()=>{try{read.value=localStorage.getItem(key())===version();}catch{read.value=false;}},{immediate:true});
function toggle(){try{if(read.value)localStorage.removeItem(key());else localStorage.setItem(key(),version());read.value=!read.value;error.value='';}catch{error.value='本机阅读状态保存失败';}}
</script>
<template><span class="read-state"><button @click="toggle" :aria-pressed="read">{{read?'已读（本机）':'标记已读（本机）'}}</button><span v-if="error" role="alert">{{error}}</span></span></template>
