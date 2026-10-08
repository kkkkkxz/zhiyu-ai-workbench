class ZwskVoicePcmWorklet extends AudioWorkletProcessor {
  constructor(options) {
    super();
    const opts = options?.processorOptions || {};
    this.targetRate = opts.targetRate || 16000;
    this.frameSamples = opts.frameSamples || 320;
    this.inputGain = opts.inputGain || 1;
    this.ratio = sampleRate / this.targetRate;
    this.pending = [];
    this.readIndex = 0;
    this.out = [];
    this.active = true;
    this.port.onmessage = (event) => {
      const data = event.data || {};
      if (data.type === 'stop') this.active = false;
      if (data.type === 'start') this.active = true;
    };
  }

  process(inputs, outputs) {
    const input = inputs[0];
    if (!this.active || !input || !input[0] || input[0].length === 0) return true;
    const ch = input[0];
    for (let i = 0; i < ch.length; i += 1) {
      this.pending.push(ch[i]);
    }

    while (this.readIndex + this.ratio < this.pending.length) {
      const idx = Math.floor(this.readIndex);
      const nextIdx = Math.min(idx + 1, this.pending.length - 1);
      const frac = this.readIndex - idx;
      const sample = (this.pending[idx] * (1 - frac) + this.pending[nextIdx] * frac) * this.inputGain;
      this.out.push(Math.max(-1, Math.min(1, sample)));
      this.readIndex += this.ratio;

      if (this.out.length >= this.frameSamples) {
        const ab = new ArrayBuffer(this.frameSamples * 2);
        const view = new DataView(ab);
        let sumSq = 0;
        let peak = 0;
        for (let j = 0; j < this.frameSamples; j += 1) {
          const v = this.out[j];
          sumSq += v * v;
          const av = Math.abs(v);
          if (av > peak) peak = av;
          const s = v < 0 ? v * 0x8000 : v * 0x7fff;
          view.setInt16(j * 2, s, true);
        }
        this.out = this.out.slice(this.frameSamples);
        this.port.postMessage(
          {
            type: 'audio',
            buffer: ab,
            rms: Math.sqrt(sumSq / this.frameSamples),
            peak,
            inputRate: sampleRate,
            targetRate: this.targetRate,
          },
          [ab],
        );
      }
    }

    if (this.readIndex > 2048) {
      const drop = Math.floor(this.readIndex);
      this.pending = this.pending.slice(drop);
      this.readIndex -= drop;
    }
    const output = outputs && outputs[0] && outputs[0][0];
    if (output) output.fill(0);
    return true;
  }
}

registerProcessor('zwsk-voice-pcm-worklet', ZwskVoicePcmWorklet);
