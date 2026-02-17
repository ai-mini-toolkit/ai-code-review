import Mustache from 'mustache';

/**
 * 验证 Mustache 模板语法
 */
export function validateMustacheSyntax(template: string): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  try {
    Mustache.parse(template);
  } catch (error: any) {
    errors.push(error.message);
    return { valid: false, errors };
  }

  return { valid: true, errors };
}

/**
 * 渲染 Mustache 模板（客户端预览）
 */
export function renderMustacheTemplate(
  template: string,
  context: Record<string, any>,
): string {
  try {
    return Mustache.render(template, context);
  } catch (error: any) {
    throw new Error(`模板渲染失败: ${error.message}`);
  }
}

/**
 * 提取模板中使用的变量名
 */
export function extractTemplateVariables(template: string): string[] {
  const variables = new Set<string>();
  try {
    const tokens = Mustache.parse(template);
    tokens.forEach((token: any) => {
      if (token[0] === 'name' || token[0] === '#' || token[0] === '^') {
        variables.add(token[1]);
      }
    });
  } catch {
    // 语法错误时返回空列表
  }
  return Array.from(variables);
}
