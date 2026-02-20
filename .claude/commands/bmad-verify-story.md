---
name: 'verify-story'
description: '通过浏览器自动化验证 Story 的所有验收标准（AC）。自动打开浏览器，逐条执行 UI 操作、检查 API 响应、验证数据持久化，生成 Pass/Fail 验证报告。调用方式：/bmad-verify-story <story-id>，例如：/bmad-verify-story 8-1'
disable-model-invocation: true
---

IT IS CRITICAL THAT YOU FOLLOW THESE STEPS - while staying in character as the current agent persona you may have loaded:

<steps CRITICAL="TRUE">
1. Always LOAD the FULL @{project-root}/_bmad/core/tasks/workflow.xml
2. READ its entire contents - this is the CORE OS for EXECUTING the specific workflow-config @{project-root}/_bmad/bmm/workflows/4-implementation/verify-story/workflow.yaml
3. Pass the yaml path @{project-root}/_bmad/bmm/workflows/4-implementation/verify-story/workflow.yaml as 'workflow-config' parameter to the workflow.xml instructions
4. Follow workflow.xml instructions EXACTLY as written to process and follow the specific workflow config and its instructions
5. Save verification report after Step 4 completes
</steps>
