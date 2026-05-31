/**
 * useSpeechRecognition — 浏览器 Web Speech API 封装
 *
 * 使用浏览器自带的 SpeechRecognition API 进行语音识别，
 * 支持中文（zh-CN），返回实时转录文本。
 *
 * 浏览器兼容性：Chrome 33+, Edge 79+, Safari 14.1+ (部分)
 * Firefox 不支持 SpeechRecognition。
 */
import { ref, onBeforeUnmount } from "vue";

export interface UseSpeechRecognitionOptions {
  /** 语言，默认 zh-CN */
  lang?: string;
  /** 是否连续识别（持续录音），默认 false（单次识别） */
  continuous?: boolean;
  /** 是否返回中间结果，默认 true */
  interimResults?: boolean;
}

export function useSpeechRecognition(
  options: UseSpeechRecognitionOptions = {},
) {
  const { lang = "zh-CN", continuous = false, interimResults = true } = options;

  /* ── 响应式状态 ── */
  const isRecording = ref(false);
  const transcript = ref(""); // 最终转录结果
  const interimTranscript = ref(""); // 中间转录结果（实时显示）
  const isSupported = ref(false);
  const error = ref<string | null>(null);

  /* ── 内部状态 ── */
  let recognition: any = null;
  let restartCount = 0;
  const MAX_RESTARTS = 3; // 最大自动重启次数（处理 no-speech 错误）

  /* ── 检测浏览器支持 ── */
  const SpeechRecognition =
    typeof window !== "undefined"
      ? (window as any).SpeechRecognition ||
        (window as any).webkitSpeechRecognition
      : null;

  if (SpeechRecognition) {
    isSupported.value = true;
    recognition = new SpeechRecognition();
    recognition.lang = lang;
    recognition.continuous = continuous;
    recognition.interimResults = interimResults;
    recognition.maxAlternatives = 1;
  }

  /* ── 事件回调 ── */
  function setupCallbacks() {
    if (!recognition) return;

    recognition.onstart = () => {
      isRecording.value = true;
      error.value = null;
      restartCount = 0;
    };

    recognition.onresult = (event: any) => {
      let interim = "";
      let final = "";

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        const text = result[0].transcript;

        if (result.isFinal) {
          final += text;
        } else {
          interim += text;
        }
      }

      if (final) {
        transcript.value += final;
      }
      interimTranscript.value = interim;
    };

    recognition.onerror = (event: any) => {
      const err = event.error;
      console.warn(`[SpeechRecognition] 错误: ${err}`);

      switch (err) {
        case "no-speech":
          // 没有检测到语音，自动重启（有限次数）
          if (restartCount < MAX_RESTARTS) {
            restartCount++;
            try {
              recognition.stop();
              setTimeout(() => {
                if (isRecording.value) {
                  recognition.start();
                }
              }, 300);
            } catch {
              /* ignore */
            }
          } else {
            error.value = "未检测到语音，请重试";
            isRecording.value = false;
          }
          break;

        case "audio-capture":
          error.value = "无法访问麦克风，请检查权限设置";
          isRecording.value = false;
          break;

        case "not-allowed":
          error.value = "麦克风权限被拒绝，请在浏览器设置中允许";
          isRecording.value = false;
          break;

        case "network":
          error.value = "网络错误，语音识别需要网络连接";
          isRecording.value = false;
          break;

        case "aborted":
          // 用户主动取消，不报错
          break;

        default:
          error.value = `语音识别错误: ${err}`;
          isRecording.value = false;
      }
    };

    recognition.onend = () => {
      isRecording.value = false;
      interimTranscript.value = "";
    };
  }

  /* ── 公开方法 ── */

  /** 开始录音 */
  function start() {
    if (!recognition) {
      error.value = "当前浏览器不支持语音识别，请使用 Chrome 或 Edge";
      return;
    }

    if (isRecording.value) {
      console.warn("[SpeechRecognition] 已在录音中");
      return;
    }

    // 重置状态
    transcript.value = "";
    interimTranscript.value = "";
    error.value = null;
    restartCount = 0;

    setupCallbacks();

    try {
      recognition.start();
    } catch (e: any) {
      console.error("[SpeechRecognition] 启动失败:", e);
      error.value = "语音识别启动失败: " + (e.message || e);
      isRecording.value = false;
    }
  }

  /** 停止录音 */
  function stop() {
    if (!recognition || !isRecording.value) return;

    try {
      recognition.stop();
    } catch {
      /* ignore */
    }

    isRecording.value = false;
    interimTranscript.value = "";
  }

  /** 切换录音状态 */
  function toggle() {
    if (isRecording.value) {
      stop();
    } else {
      start();
    }
  }

  /** 重置转录文本 */
  function reset() {
    transcript.value = "";
    interimTranscript.value = "";
    error.value = null;
  }

  /* ── 清理 ── */
  onBeforeUnmount(() => {
    if (recognition && isRecording.value) {
      try {
        recognition.stop();
      } catch {
        /* ignore */
      }
    }
  });

  return {
    isRecording,
    transcript,
    interimTranscript,
    isSupported,
    error,
    start,
    stop,
    toggle,
    reset,
  };
}
