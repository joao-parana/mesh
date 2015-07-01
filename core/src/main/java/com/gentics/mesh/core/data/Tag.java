package com.gentics.mesh.core.data;

import java.util.List;

import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.impl.TagImpl;
import com.gentics.mesh.core.data.node.MeshNode;
import com.gentics.mesh.core.data.service.transformation.TransformationInfo;
import com.gentics.mesh.core.rest.tag.TagResponse;
import com.gentics.mesh.paging.PagingInfo;

public interface Tag extends GenericNode {

	void setName(String name);

	String getName();

	List<? extends TagFieldContainer> getFieldContainers();

	MeshUser getCreator();

	TagFamily getTagFamilyRoot();

	void addProject(Project project);

	void setCreator(MeshUser user);

	TagResponse transformToRest(TransformationInfo info);

	void removeNode(MeshNode node);

	List<? extends MeshNode> getNodes();

	void remove();

	void delete();

	TagImpl getImpl();

	Page<MeshNode> findTaggedNodes(MeshAuthUser requestUser, String projectName, List<String> languageTags, PagingInfo pagingInfo);
}
