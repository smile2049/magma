package org.obiba.magma.datasource.limesurvey;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.obiba.magma.AttributeAwareBuilder;
import org.obiba.magma.Category;
import org.obiba.magma.Datasource;
import org.obiba.magma.Initialisable;
import org.obiba.magma.NoSuchValueSetException;
import org.obiba.magma.Timestamps;
import org.obiba.magma.Value;
import org.obiba.magma.ValueSet;
import org.obiba.magma.ValueType;
import org.obiba.magma.Variable;
import org.obiba.magma.Variable.Builder;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.VariableValueSource;
import org.obiba.magma.VectorSource;
import org.obiba.magma.support.AbstractValueTable;
import org.obiba.magma.support.AbstractVariableEntityProvider;
import org.obiba.magma.support.VariableEntityBean;
import org.obiba.magma.support.VariableEntityProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class LimesurveyValueTable extends AbstractValueTable {

  private JdbcTemplate jdbcTemplate;

  private final Integer sid;

  private String tablePrefix = "";

  public LimesurveyValueTable(Datasource datasource, String name, Integer sid) {
    super(datasource, name);
    if(datasource.hasAttribute(LimesurveyDatasource.TABLE_PREFIX_KEY)) {
      this.tablePrefix = datasource.getAttribute(LimesurveyDatasource.TABLE_PREFIX_KEY).getValue().toString();
    }
    this.sid = sid;
  }

  public LimesurveyValueTable(Datasource datasource, String name, Integer sid, VariableEntityProvider variableEntityProvider) {
    this(datasource, name, sid);
    setVariableEntityProvider(variableEntityProvider);
  }

  @Override
  public void initialise() {
    super.initialise();
    initialiseVariableValueSources();
  }

  private void initialiseVariableValueSources() {
    getSources().clear();
    jdbcTemplate = ((LimesurveyDatasource) getDatasource()).getJdbcTemplate();
    Map<Integer, LimeQuestion> mapQuestions = queryQuestions();
    Map<Integer, List<LimeAnswer>> mapAnswers = Maps.newHashMap();
    mapAnswers.putAll(queryExplicitAnswers(mapQuestions));
    mapAnswers.putAll(buildImplicitAnswers(mapQuestions));
    buildVariables(mapQuestions, mapAnswers);
  }

  private Map<Integer, LimeQuestion> queryQuestions() {
    StringBuilder sqlQuestion = new StringBuilder();
    sqlQuestion.append("SELECT * FROM " + tablePrefix + "questions q JOIN " + tablePrefix + "groups g ");
    sqlQuestion.append("ON (q.gid=g.gid AND q.language=g.language) ");
    sqlQuestion.append("WHERE q.sid=? AND q.type!='X' "); // X are boilerplate questions
    sqlQuestion.append("ORDER BY group_order, question_order ASC ");
    SqlRowSet questionsRowSet = jdbcTemplate.queryForRowSet(sqlQuestion.toString(), new Object[] { sid });
    return toQuestions(questionsRowSet);
  }

  private Map<Integer, LimeQuestion> toQuestions(SqlRowSet rows) {
    Map<Integer, LimeQuestion> questions = Maps.newHashMap();
    while(rows.next()) {
      int qid = rows.getInt("qid");
      if(questions.containsKey(qid)) {
        LimeQuestion question = questions.get(qid);
        question.addLocalizableAttribute(rows.getString("language"), rows.getString("question"));
      } else {
        LimeQuestion question = LimeQuestion.create();
        question.setName(rows.getString("title"));
        question.setQid(qid);
        question.setParentQid(rows.getInt("parent_qid"));
        question.setType(LimesurveyType._valueOf(rows.getString("type")));
        question.addLocalizableAttribute(rows.getString("language"), rows.getString("question"));
        question.setUseOther("Y".equals(rows.getString("other")) ? true : false);
        question.setScaleId(rows.getInt("scale_id"));
        questions.put(qid, question);
      }
    }
    return questions;
  }

  private Map<Integer, List<LimeAnswer>> queryExplicitAnswers(Map<Integer, LimeQuestion> mapQuestions) {
    Map<Integer, List<LimeAnswer>> answers = Maps.newHashMap();
    String sqlAnswer = "SELECT * FROM " + tablePrefix + "answers WHERE qid=? ORDER BY sortorder";
    for(LimeQuestion question : mapQuestions.values()) {
      SqlRowSet answersRowset = jdbcTemplate.queryForRowSet(sqlAnswer, new Object[] { question.getQid() });
      List<LimeAnswer> answersList = toAnswers(question, answersRowset);
      answers.put(question.getQid(), answersList);
    }
    return answers;
  }

  private List<LimeAnswer> toAnswers(LimeQuestion question, SqlRowSet rows) {
    List<LimeAnswer> answers = Lists.newArrayList();
    Map<String, LimeAnswer> internAnswers = Maps.newHashMap();
    while(rows.next()) {
      String answerName = rows.getString("code");
      String language = rows.getString("language");
      String label = rows.getString("answer");
      Integer scaleId = rows.getInt("scale_id");
      if(internAnswers.containsKey(answerName + scaleId)) {
        LimeAnswer answer = internAnswers.get(answerName + scaleId);
        answer.addLocalizableAttribute(language, label);
      } else {
        LimeAnswer answer = LimeAnswer.create(answerName);
        answer.setSortorder(rows.getInt("sortorder"));
        answer.setScaleId(rows.getInt("scale_id"));
        answer.addLocalizableAttribute(language, label);
        internAnswers.put(answerName + scaleId, answer);
        answers.add(answer);
      }
    }
    if(question.isUseOther()) {
      LimeAnswer answer = LimeAnswer.create("-oth-");
      answers.add(answer);
    }
    return answers;
  }

  private Map<Integer, List<LimeAnswer>> buildImplicitAnswers(Map<Integer, LimeQuestion> mapQuestions) {
    Map<Integer, List<LimeAnswer>> mapAnswers = Maps.newHashMap();
    for(Integer qid : mapQuestions.keySet()) {
      LimeQuestion question = mapQuestions.get(qid);
      LimesurveyType type = question.getLimesurveyType();
      List<LimeAnswer> answers = Lists.newArrayList();
      if(type.hasImplicitCategories()) {
        for(String implicitAnswer : type.getImplicitAnswers()) {
          LimeAnswer answer = LimeAnswer.create(implicitAnswer);
          answers.add(answer);
        }
        mapAnswers.put(qid, answers);
      }
    }
    return mapAnswers;
  }

  private void buildVariables(Map<Integer, LimeQuestion> mapQuestions, Map<Integer, List<LimeAnswer>> mapAnswers) {
    try {
      for(LimeQuestion question : mapQuestions.values()) {
        if(buildRanking(question, mapAnswers) == false) {
          LimeQuestion parentQuestion = null;
          boolean isDualScaleCase = false;
          boolean isArraySubQuestionCase = false;
          // here are managed special case
          if(question.hasParentId()) {
            parentQuestion = getParentQuestion(question, mapQuestions);
            isArraySubQuestionCase = buildArraySubQuestions(question, parentQuestion, mapQuestions);
            isDualScaleCase = buildArrayDualScale(question, parentQuestion, mapQuestions, mapAnswers);
          }
          Variable.Builder vb = null;
          if(isArraySubQuestionCase == false && isDualScaleCase == false) {
            vb = buildVariable(mapQuestions, question);
          }
          buildOtherVariableIfNecessary(question);
          buildCommentVariableIfNecessary(question, parentQuestion, mapQuestions);

          // we stop if we already build 'special' variables cases
          if(vb != null) {
            buildAttributes(question, vb);
            if(question.hasParentId()) {
              buildCategoriesForVariable(question, vb, mapAnswers.get(parentQuestion.getQid()));
            } else if(hasSubQuestions(question, mapQuestions) == false) {
              buildCategoriesForVariable(question, vb, mapAnswers.get(question.getQid()));
            }
            LimesurveyVariableValueSource variable = new LimesurveyVariableValueSource(vb.build());
            addVariableValueSource(variable);
          }
        }
      }
    } catch(Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private void buildAttributes(LimeLocalizableEntity localizable, AttributeAwareBuilder<?> builder) {
    applyImplicitLabel(localizable, builder);
    for(Map.Entry<String, String> attr : localizable.getLocalizableLabel().entrySet()) {
      builder.addAttribute(attr.getKey(), attr.getValue());
    }
  }

  private void applyImplicitLabel(LimeLocalizableEntity localizable, AttributeAwareBuilder<?> builder) {
    LimeLocalizableAttributes lla = localizable.getImplicitLabel().get(localizable.getName());
    if(lla != null) {
      for(Map.Entry<String, String> attrs : lla.getAttributes().entrySet()) {
        builder.addAttribute(attrs.getKey(), attrs.getValue());
      }
    }
  }

  private boolean buildRanking(LimeQuestion question, Map<Integer, List<LimeAnswer>> mapAnswers) {
    if(question.getLimesurveyType() == LimesurveyType.RANKING) {
      List<LimeAnswer> answers = mapAnswers.get(question.getQid());
      for(int nbChoices = 1; nbChoices < answers.size() + 1; nbChoices++) {
        Variable.Builder vb = Variable.Builder.newVariable(question.getName() + " [" + nbChoices + "]", question.getLimesurveyType().getType(), "Participant");
        LimesurveyVariableValueSource variable = new LimesurveyVariableValueSource(vb.build());
        addVariableValueSource(variable);
      }
      return true;
    }
    return false;
  }

  private boolean buildArrayDualScale(LimeQuestion question, LimeQuestion parentQuestion, Map<Integer, LimeQuestion> mapQuestions, Map<Integer, List<LimeAnswer>> mapAnswers) {
    if(parentQuestion.getLimesurveyType() == LimesurveyType.ARRAY_DUAL_SCALE) {
      for(int scale = 0; scale < 2; scale++) {
        String hierarchicalVariableName = parentQuestion.getName() + " [" + question.getName() + "][" + scale + "]";
        Variable.Builder vb = Variable.Builder.newVariable(hierarchicalVariableName, question.getLimesurveyType().getType(), "Participant");
        buildAttributes(question, vb);
        List<LimeAnswer> answers = mapAnswers.get(parentQuestion.getQid());
        for(LimeAnswer answer : answers) {
          if(scale == answer.getScaleId()) {
            Category.Builder cb = Category.Builder.newCategory(answer.getName());
            buildAttributes(answer, cb);
            vb.addCategory(cb.build());
          }
        }
        Variable build = vb.build();
        LimesurveyVariableValueSource variable = new LimesurveyVariableValueSource(build);
        addVariableValueSource(variable);
      }
      return true;
    }
    return false;
  }

  private Variable.Builder buildVariable(Map<Integer, LimeQuestion> mapQuestions, LimeQuestion question) {
    Variable.Builder vb;
    // do not create variable for parent question
    if(hasSubQuestions(question, mapQuestions) == false) {
      String variableName = question.getName();
      if(question.hasParentId()) {
        LimeQuestion parentQuestion = getParentQuestion(question, mapQuestions);
        String hierarchicalVariableName = parentQuestion.getName() + " [" + variableName + "]";
        vb = Variable.Builder.newVariable(hierarchicalVariableName, parentQuestion.getLimesurveyType().getType(), "Participant");
      } else {
        vb = Variable.Builder.newVariable(variableName, question.getLimesurveyType().getType(), "Participant");
      }
      return vb;
    }
    // question has subquestion then return null
    return null;
  }

  private boolean buildArraySubQuestions(LimeQuestion question, LimeQuestion parentQuestion, Map<Integer, LimeQuestion> mapQuestions) {
    List<LimeQuestion> scalableSubQuestions = getScaledOneSubQuestions(parentQuestion, mapQuestions);
    if(scalableSubQuestions.isEmpty()) return false;
    if(question.isScaleEqual1() == false) {
      for(LimeQuestion scalableQuestion : scalableSubQuestions) {
        String arrayVariableName = parentQuestion.getName() + " [" + question.getName() + "_" + scalableQuestion.getName() + "]";
        Variable.Builder subVb = Variable.Builder.newVariable(arrayVariableName, parentQuestion.getLimesurveyType().getType(), "Participant");
        buildAttributes(scalableQuestion, subVb);
        LimesurveyVariableValueSource variable = new LimesurveyVariableValueSource(subVb.build());
        addVariableValueSource(variable);
      }
    }
    return true;
  }

  private void buildOtherVariableIfNecessary(LimeQuestion question) {
    if(question.isUseOther()) {
      Builder other = Variable.Builder.newVariable(question.getName() + " [other]", question.getLimesurveyType().getType(), "Participant");
      buildSpecialLabel(question, other, "other");
      addVariableValueSource(new LimesurveyVariableValueSource(other.build()));
    }
  }

  /**
   * Special label are "other" or "comment"
   * @param question
   * @param builder
   */
  private void buildSpecialLabel(LimeQuestion question, Builder builder, String specialLabel) {
    for(Map.Entry<String, String> attrs : question.getImplicitLabel().get(specialLabel).getAttributes().entrySet()) {
      builder.addAttribute(attrs.getKey(), attrs.getValue());
    }
  }

  private void buildCommentVariableIfNecessary(LimeQuestion question, LimeQuestion parentQuestion, Map<Integer, LimeQuestion> mapQuestions) {
    if(question.getLimesurveyType().isCommentable() && hasSubQuestions(question, mapQuestions) == false) {
      Builder comment = Variable.Builder.newVariable(question.getName() + " [comment]", question.getLimesurveyType().getType(), "Participant");
      buildSpecialLabel(question, comment, "comment");
      addVariableValueSource(new LimesurveyVariableValueSource(comment.build()));
    } else if(parentQuestion != null && parentQuestion.getLimesurveyType().isCommentable()) {
      String hierarchicalVariableName = parentQuestion.getName() + " [" + question.getName() + "comment]";
      Builder comment = Variable.Builder.newVariable(hierarchicalVariableName, question.getLimesurveyType().getType(), "Participant");
      buildSpecialLabel(question, comment, "comment");
      addVariableValueSource(new LimesurveyVariableValueSource(comment.build()));
    }
  }

  private void buildCategoriesForVariable(LimeQuestion question, Variable.Builder vb, List<LimeAnswer> limeAnswers) {
    for(LimeAnswer answer : limeAnswers) {
      Category.Builder cb = Category.Builder.newCategory(answer.getName());
      buildAttributes(answer, cb);
      vb.addCategory(cb.build());
    }
  }

  private LimeQuestion getParentQuestion(LimeQuestion limeQuestion, Map<Integer, LimeQuestion> mapQuestions) {
    if(limeQuestion.hasParentId()) {
      return mapQuestions.get(limeQuestion.getParentQid());
    }
    return null;
  }

  private boolean hasSubQuestions(final LimeQuestion limeQuestion, Map<Integer, LimeQuestion> mapQuestions) {
    return Iterables.any(mapQuestions.values(), new Predicate<LimeQuestion>() {
      @Override
      public boolean apply(LimeQuestion question) {
        return question.getParentQid() == limeQuestion.getQid();
      }
    });
  }

  private List<LimeQuestion> getScaledOneSubQuestions(final LimeQuestion limeQuestion, Map<Integer, LimeQuestion> mapQuestions) {
    return Lists.newArrayList(Iterables.filter(mapQuestions.values(), new Predicate<LimeQuestion>() {
      @Override
      public boolean apply(LimeQuestion question) {
        return question.getParentQid() == limeQuestion.getQid() && question.isScaleEqual1();
      }
    }));
  }

  @Override
  public LimesurveyDatasource getDatasource() {
    return (LimesurveyDatasource) super.getDatasource();
  }

  public Integer getSid() {
    return sid;
  }

  @Override
  public ValueSet getValueSet(VariableEntity entity) throws NoSuchValueSetException {
    return new LimesurveyValueSet(this, entity);
  }

  @Override
  public Timestamps getTimestamps() {
    // TODO Auto-generated method stub
    return null;
  }

  class LimesurveyVariableEntityProvider extends AbstractVariableEntityProvider implements Initialisable {

    private HashSet<VariableEntity> entities;

    protected LimesurveyVariableEntityProvider(String entityType) {
      super(entityType);
    }

    @Override
    public void initialise() {
      String sqlEntities = "SELECT " + tablePrefix + "token FROM survey_" + getSid();
      List<VariableEntity> entityList = getDatasource().getJdbcTemplate().query(sqlEntities, new RowMapper<VariableEntity>() {

        @Override
        public VariableEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
          String entityId = rs.getString("token");
          return new VariableEntityBean("Participant", entityId);
        }
      });
      entities = Sets.newHashSet(entityList);
    }

    @Override
    public Set<VariableEntity> getVariableEntities() {
      return Collections.unmodifiableSet(entities);
    }
  }

  class LimesurveyVariableValueSource implements VariableValueSource {

    private Variable variable;

    public LimesurveyVariableValueSource(Variable variable) {
      this.variable = variable;
    }

    @Override
    public ValueType getValueType() {
      return variable.getValueType();
    }

    @Override
    public Value getValue(ValueSet valueSet) {
      LimesurveyValueSet limesurveyValueSet = (LimesurveyValueSet) valueSet;
      return limesurveyValueSet.getValue(variable);
    }

    @Override
    public VectorSource asVectorSource() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Variable getVariable() {
      return variable;
    }

  }
}
