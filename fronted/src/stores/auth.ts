import { ref, computed } from "vue";
import { defineStore } from "pinia";
import { api, type ProfileResponse } from "@/utils/request";

export const useAuthStore = defineStore("auth", () => {
  const token = ref(localStorage.getItem("token") || "");
  const userId = ref<number>(0);
  const username = ref("");
  const profile = ref<ProfileResponse | null>(null);
  const isLoggedIn = computed(() => !!token.value);

  function setToken(newToken: string) {
    token.value = newToken;
    localStorage.setItem("token", newToken);
  }

  function clearAuth() {
    token.value = "";
    userId.value = 0;
    username.value = "";
    profile.value = null;
    localStorage.removeItem("token");
  }

  async function login(loginUsername: string, password: string) {
    const res = await api.login({ username: loginUsername, password });
    if (res.code === 200 && res.data) {
      setToken(res.data.token);
      userId.value = res.data.userId;
      username.value = res.data.username;
      await fetchProfile();
    }
    return res;
  }

  async function register(
    regUsername: string,
    password: string,
    email?: string,
  ) {
    const res = await api.register({ username: regUsername, password, email });
    if (res.code === 200 && res.data) {
      setToken(res.data.token);
      userId.value = res.data.userId;
      username.value = res.data.username;
      await fetchProfile();
    }
    return res;
  }

  async function fetchProfile() {
    try {
      const res = await api.getProfile();
      if (res.code === 200 && res.data) {
        profile.value = res.data;
        username.value = res.data.username;
      }
    } catch {
      // ignore
    }
  }

  function logout() {
    clearAuth();
  }

  return {
    token,
    userId,
    username,
    profile,
    isLoggedIn,
    login,
    register,
    fetchProfile,
    logout,
    clearAuth,
  };
});
