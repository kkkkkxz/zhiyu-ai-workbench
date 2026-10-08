import type { Persona } from '@/types/api';

export interface AssistantPersona extends Persona {
  title: string;
  initials: string;
  accent: string;
  domain: string;
  strengths: string[];
  prompts: string[];
}

export const PERSONA_STORAGE_KEY = 'zwsk.persona.selectedId';
export const PERSONA_SESSION_STORAGE_KEY = 'zwsk.persona.sessionMap';

export const ASSISTANT_PERSONAS: AssistantPersona[] = [
  {
    id: 'general',
    name: '行业 AI 知识助手',
    title: 'AI 技术与行业应用问答',
    description: '专注于 AI 技术、RAG 知识库、企业知识库、智能体、私有化部署和行业落地方案。',
    tone: '专业、清晰、实用',
    initials: 'AI',
    accent: 'from-zinc-900 via-slate-700 to-cyan-600',
    domain: 'AI 技术',
    strengths: ['RAG 知识库', '私有化部署', '行业方案'],
    prompts: [
      '什么是 RAG，适合什么场景',
      '企业私有知识库怎么搭建',
      '内网大模型部署需要考虑什么',
      '怎么做向量检索和文档解析',
    ],
  },
  {
    id: 'receptionist',
    name: '前台接待专员',
    title: '访客接待与企业介绍',
    description: '面向访客、客户和外部伙伴，擅长公司介绍、路线指引、会议接待。',
    tone: '礼貌、热情、口语化',
    initials: 'RC',
    accent: 'from-sky-700 via-cyan-600 to-emerald-500',
    domain: '接待服务',
    strengths: ['访客接待', '路线指引', '公司介绍'],
    prompts: [
      '介绍一下我们公司的主营业务',
      '帮我写一段访客接待欢迎语',
      '整理会议来访接待流程',
      '用对外口吻介绍公司优势',
    ],
  },
  {
    id: 'onboarding_trainer',
    name: '新员工培训员',
    title: '入职培训与岗位上手',
    description: '适合新人入职培训、制度讲解、岗位流程说明和工具使用辅导。',
    tone: '耐心、清楚、分步骤',
    initials: 'OT',
    accent: 'from-indigo-700 via-sky-600 to-teal-500',
    domain: '员工培训',
    strengths: ['入职引导', '制度讲解', '流程演练'],
    prompts: [
      '帮我生成一份新人入职培训提纲',
      '把这个制度讲给新员工听',
      '设计一个岗位流程学习清单',
      '整理常用工具的培训话术',
    ],
  },
  {
    id: 'process_consultant',
    name: '制度流程顾问',
    title: '内部制度与流程合规',
    description: '面向报销、请假、审批、用章、采购等内部流程，回答更强调规范和边界。',
    tone: '严谨、流程化、合规',
    initials: 'PC',
    accent: 'from-slate-900 via-blue-700 to-amber-500',
    domain: '流程制度',
    strengths: ['审批规则', '流程梳理', '合规提醒'],
    prompts: [
      '报销流程需要准备哪些材料',
      '帮我梳理请假审批的关键节点',
      '把采购流程整理成步骤清单',
      '解释一下用章申请的注意事项',
    ],
  },
  {
    id: 'product_briefing',
    name: '产品讲解员',
    title: '产品介绍与方案表达',
    description: '适合产品介绍、功能说明、方案亮点提炼和对外演示口径整理。',
    tone: '清晰、专业、偏对外表达',
    initials: 'PB',
    accent: 'from-cyan-700 via-blue-600 to-violet-600',
    domain: '产品方案',
    strengths: ['产品卖点', '功能讲解', '方案包装'],
    prompts: [
      '帮我介绍这个产品的核心价值',
      '把功能清单整理成客户听得懂的话',
      '生成一版产品演示开场白',
      '提炼这套方案的三个亮点',
    ],
  },
  {
    id: 'support_specialist',
    name: '售后客服专员',
    title: '问题处理与服务跟进',
    description: '面向售后、投诉解释、服务进度同步和客户安抚。',
    tone: '负责、安抚、明确下一步',
    initials: 'SS',
    accent: 'from-emerald-700 via-teal-600 to-sky-500',
    domain: '客户服务',
    strengths: ['问题定位', '投诉处理', '进度同步'],
    prompts: [
      '帮我回复客户这个问题',
      '整理一版售后处理话术',
      '把投诉问题拆成处理步骤',
      '生成服务进度同步模板',
    ],
  },
  {
    id: 'tech_support',
    name: '技术支持工程师',
    title: '故障排查与配置说明',
    description: '适合系统故障、使用排查、配置说明和技术问题定位。',
    tone: '直接、步骤化、工程化',
    initials: 'TS',
    accent: 'from-slate-900 via-cyan-700 to-lime-500',
    domain: '技术支持',
    strengths: ['故障排查', '配置说明', '技术定位'],
    prompts: [
      '帮我排查这个系统异常',
      '把配置步骤整理成操作手册',
      '解释这条错误日志可能的原因',
      '生成一份技术支持回复',
    ],
  },
  {
    id: 'exec_briefing',
    name: '高管简报秘书',
    title: '管理汇报与重点提炼',
    description: '适合汇总文档、提炼重点、生成汇报口径和管理层视角摘要。',
    tone: '精炼、重点优先、管理层视角',
    initials: 'EB',
    accent: 'from-zinc-950 via-stone-700 to-yellow-500',
    domain: '经营简报',
    strengths: ['重点提炼', '汇报口径', '管理摘要'],
    prompts: [
      '把这份材料整理成高管简报',
      '提炼三个最重要的管理结论',
      '生成一版会议汇报口径',
      '按风险、进展、下一步整理内容',
    ],
  },
];

export const FALLBACK_PERSONAS: Persona[] = ASSISTANT_PERSONAS.map(
  ({ id, name, description, tone }) => ({ id, name, description, tone }),
);

export function getStoredPersonaId() {
  try {
    const raw = localStorage.getItem(PERSONA_STORAGE_KEY);
    if (!raw) return 'general';
    if (raw.startsWith('{')) {
      const parsed = JSON.parse(raw) as { state?: { personaId?: string }; personaId?: string };
      return parsed.state?.personaId || parsed.personaId || 'general';
    }
    return raw;
  } catch {
    return 'general';
  }
}

export function storePersonaId(personaId: string) {
  try {
    localStorage.setItem(PERSONA_STORAGE_KEY, personaId || 'general');
  } catch {}
}

export function readSessionPersonaMap(): Record<string, string> {
  try {
    const raw = localStorage.getItem(PERSONA_SESSION_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

export function getSessionPersonaId(sessionId?: string | number | null) {
  if (!sessionId) return undefined;
  return readSessionPersonaMap()[String(sessionId)];
}

export function getSessionPersonaProfile(sessionId?: string | number | null, sessionTitle?: string | null) {
  const savedId = getSessionPersonaId(sessionId);
  if (savedId) return getPersonaProfile(savedId);

  const title = sessionTitle || '';
  const inferred = ASSISTANT_PERSONAS.find((persona) => title.startsWith(`${persona.name} ·`));
  return inferred ?? getPersonaProfile('general');
}

export function storeSessionPersonaId(sessionId: string | number, personaId: string) {
  try {
    const map = readSessionPersonaMap();
    map[String(sessionId)] = personaId || 'general';
    localStorage.setItem(PERSONA_SESSION_STORAGE_KEY, JSON.stringify(map));
  } catch {}
}

export function getPersonaProfile(personaId?: string, remote?: Persona | null): AssistantPersona {
  const id = personaId || remote?.id || 'general';
  const local = ASSISTANT_PERSONAS.find((item) => item.id === id) ?? ASSISTANT_PERSONAS[0];
  if (!remote) return local;
  return {
    ...local,
    id: remote.id || local.id,
    name: remote.name || local.name,
    description: remote.description || local.description,
    tone: remote.tone || local.tone,
  };
}

export function mergePersonaProfiles(remote: Persona[] = []) {
  const byId = new Map<string, Persona>();
  remote.forEach((item) => byId.set(item.id, item));

  const merged = ASSISTANT_PERSONAS.map((item) => getPersonaProfile(item.id, byId.get(item.id)));
  remote.forEach((item) => {
    if (ASSISTANT_PERSONAS.some((local) => local.id === item.id)) return;
    merged.push({
      ...ASSISTANT_PERSONAS[0],
      id: item.id,
      name: item.name,
      title: item.name,
      description: item.description || '企业自定义助手人格。',
      tone: item.tone || '专业、清晰、可靠',
      initials: item.name.slice(0, 2).toUpperCase(),
      domain: '自定义',
      strengths: ['定制回答', '知识库问答', '业务协助'],
      prompts: ASSISTANT_PERSONAS[0].prompts,
    });
  });
  return merged;
}
