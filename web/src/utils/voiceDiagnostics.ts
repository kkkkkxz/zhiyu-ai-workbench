/**
 * 语音对话诊断工具
 * 用于排查麦克风、WebSocket、ASR 等问题
 */

import { buildVoiceV3WebSocketUrl } from '@/voice/v3/protocol';

export interface DiagnosticResult {
  category: string;
  status: 'success' | 'warning' | 'error';
  message: string;
  details?: any;
}

export async function runVoiceDiagnostics(token?: string | null): Promise<DiagnosticResult[]> {
  const results: DiagnosticResult[] = [];

  // 1. 检查浏览器兼容性
  results.push(checkBrowserSupport());

  // 2. 检查麦克风权限
  results.push(await checkMicrophonePermission());

  // 3. 检查音频采集
  results.push(await checkAudioCapture());

  // 4. 检查 WebSocket 连接
  if (token) {
    results.push(await checkWebSocketConnection(token));
  }

  return results;
}

function checkBrowserSupport(): DiagnosticResult {
  const hasGetUserMedia = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  const hasWebSocket = typeof WebSocket !== 'undefined';
  const hasAudioContext = !!(window.AudioContext || (window as any).webkitAudioContext);

  if (hasGetUserMedia && hasWebSocket && hasAudioContext) {
    return {
      category: '浏览器兼容性',
      status: 'success',
      message: '浏览器支持所有必需功能',
      details: { hasGetUserMedia, hasWebSocket, hasAudioContext },
    };
  }

  return {
    category: '浏览器兼容性',
    status: 'error',
    message: '浏览器不支持部分功能',
    details: { hasGetUserMedia, hasWebSocket, hasAudioContext },
  };
}

async function checkMicrophonePermission(): Promise<DiagnosticResult> {
  try {
    const result = await navigator.permissions.query({ name: 'microphone' as PermissionName });

    if (result.state === 'granted') {
      return {
        category: '麦克风权限',
        status: 'success',
        message: '麦克风权限已授予',
        details: { state: result.state },
      };
    } else if (result.state === 'prompt') {
      return {
        category: '麦克风权限',
        status: 'warning',
        message: '需要请求麦克风权限',
        details: { state: result.state },
      };
    } else {
      return {
        category: '麦克风权限',
        status: 'error',
        message: '麦克风权限被拒绝',
        details: { state: result.state },
      };
    }
  } catch (err) {
    return {
      category: '麦克风权限',
      status: 'warning',
      message: '无法查询权限状态（某些浏览器不支持）',
      details: { error: String(err) },
    };
  }
}

async function checkAudioCapture(): Promise<DiagnosticResult> {
  let stream: MediaStream | null = null;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        sampleRate: 16000,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
      },
    });

    const tracks = stream.getAudioTracks();
    if (tracks.length === 0) {
      return {
        category: '音频采集',
        status: 'error',
        message: '无法获取音频轨道',
      };
    }

    const track = tracks[0];
    const settings = track.getSettings();

    // 测试音频处理
    const Ctor = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext;
    const ctx = new Ctor({ sampleRate: 16000 });
    const source = ctx.createMediaStreamSource(stream);
    const proc = ctx.createScriptProcessor(4096, 1, 1);

    let frameCount = 0;
    let hasAudio = false;

    await new Promise<void>((resolve) => {
      proc.onaudioprocess = (e) => {
        frameCount++;
        const ch = e.inputBuffer.getChannelData(0);
        let sumSq = 0;
        for (let i = 0; i < ch.length; i++) {
          sumSq += ch[i] * ch[i];
        }
        const rms = Math.sqrt(sumSq / ch.length);
        if (rms > 0.001) hasAudio = true;

        if (frameCount >= 5) {
          proc.disconnect();
          source.disconnect();
          ctx.close();
          resolve();
        }
      };

      source.connect(proc);
      proc.connect(ctx.destination);

      setTimeout(() => {
        try {
          proc.disconnect();
          source.disconnect();
          ctx.close();
        } catch {}
        resolve();
      }, 2000);
    });

    return {
      category: '音频采集',
      status: hasAudio ? 'success' : 'warning',
      message: hasAudio ? '音频采集正常' : '未检测到音频信号（请说话测试）',
      details: {
        sampleRate: settings.sampleRate,
        channelCount: settings.channelCount,
        frameCount,
        hasAudio,
      },
    };
  } catch (err: any) {
    return {
      category: '音频采集',
      status: 'error',
      message: `音频采集失败: ${err.message || String(err)}`,
      details: { error: String(err) },
    };
  } finally {
    if (stream) {
      stream.getTracks().forEach((t) => t.stop());
    }
  }
}

async function checkWebSocketConnection(token: string): Promise<DiagnosticResult> {
  return new Promise((resolve) => {
    const url = buildVoiceV3WebSocketUrl({ token });
    const safeUrl = redactUrlToken(url);

    const ws = new WebSocket(url);
    ws.binaryType = 'arraybuffer';

    let opened = false;
    let receivedMessage = false;

    const timeout = setTimeout(() => {
      ws.close();
      resolve({
        category: 'WebSocket 连接',
        status: 'error',
        message: '连接超时（5秒内未建立）',
        details: { url: safeUrl, opened, receivedMessage },
      });
    }, 5000);

    ws.onopen = () => {
      opened = true;
      ws.send(JSON.stringify({ type: 'PING' }));
    };

    ws.onmessage = (ev) => {
      if (typeof ev.data === 'string') {
        receivedMessage = true;
        clearTimeout(timeout);
        ws.close();

        try {
          const msg = JSON.parse(ev.data);
          resolve({
            category: 'WebSocket 连接',
            status: 'success',
            message: 'WebSocket 连接正常',
            details: { url: safeUrl, opened, receivedMessage, firstMessage: msg },
          });
        } catch {
          resolve({
            category: 'WebSocket 连接',
            status: 'warning',
            message: 'WebSocket 已连接但消息格式异常',
            details: { url: safeUrl, opened, receivedMessage },
          });
        }
      }
    };

    ws.onerror = () => {
      clearTimeout(timeout);
      resolve({
        category: 'WebSocket 连接',
        status: 'error',
        message: 'WebSocket 连接失败',
        details: { url: safeUrl, opened, receivedMessage },
      });
    };

    ws.onclose = () => {
      clearTimeout(timeout);
      if (!receivedMessage) {
        resolve({
          category: 'WebSocket 连接',
          status: 'error',
          message: opened ? 'WebSocket 连接后立即关闭' : 'WebSocket 无法建立连接',
          details: { url: safeUrl, opened, receivedMessage },
        });
      }
    };
  });
}

function redactUrlToken(raw: string) {
  try {
    const url = new URL(raw);
    if (url.searchParams.has('token')) {
      url.searchParams.set('token', '***');
    }
    return url.toString();
  } catch {
    return raw.replace(/([?&]token=)[^&]+/i, '$1***');
  }
}

export function printDiagnostics(results: DiagnosticResult[]) {
  console.group('🔍 语音对话诊断报告');

  results.forEach((result) => {
    const icon = result.status === 'success' ? '✅' : result.status === 'warning' ? '⚠️' : '❌';
    console.group(`${icon} ${result.category}`);
    console.log('状态:', result.status);
    console.log('消息:', result.message);
    if (result.details) {
      console.log('详情:', result.details);
    }
    console.groupEnd();
  });

  console.groupEnd();
}
